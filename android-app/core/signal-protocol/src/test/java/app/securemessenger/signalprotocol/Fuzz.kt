package app.securemessenger.signalprotocol

import java.util.Random
import kotlin.reflect.KClass
import org.junit.Assert.fail

/** One fuzz input. [description] says how it was made (position and value), never its bytes. */
class Mutant(val description: String, val bytes: ByteArray)

/**
 * Deterministic mutation fuzzing for the Phase 5 security test pass. Every run uses the same seeds,
 * so a failure is reproducible from its description alone. This source set also runs on the phones,
 * where the sampled parts shrink to a third; `-Dsmp.fuzz.scale=N` multiplies them for a deeper JVM
 * run. Mutants are generated lazily, one at a time.
 */
object Fuzz {
    val scale: Int = (System.getProperty("smp.fuzz.scale")?.toIntOrNull() ?: 1).coerceAtLeast(1)

    private val onDevice: Boolean = System.getProperty("java.vm.vendor").orEmpty().contains("Android")

    /** How many of [jvmCount] sampled inputs to run here. */
    fun budget(jvmCount: Int): Int = if (onDevice) maxOf(1, jvmCount / 3) else jvmCount * scale

    /** Values that length, count, version, and type fields most often mishandle. */
    private val BOUNDARY_BYTES = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x7F, 0x80, 0xFF)

    /** Length-like values written over 16- and 32-bit windows. */
    private val BOUNDARY_INTS = longArrayOf(0, 1, 0x7F, 0x80, 0xFFFF, 0x10000, 0x7FFFFFFF, 0x80000000, 0xFFFFFFFF)

    private const val STRUCTURAL_EDITS = 200

    /**
     * Exhaustive single-position mutants of [valid] (one random bit flip and
     * [boundaryValuesPerPosition] boundary values at every byte not in [skip]), every truncation,
     * and sampled structural edits (insertions, deletions, extensions, length-like overwrites).
     */
    fun mutants(
        valid: ByteArray,
        random: Random,
        boundaryValuesPerPosition: Int = BOUNDARY_BYTES.size,
        skip: IntRange = IntRange.EMPTY,
    ): Sequence<Mutant> =
        sequence {
            for (position in valid.indices) {
                if (position in skip) continue
                yield(flip(valid, position, random.nextInt(8)))
                for (index in 0 until boundaryValuesPerPosition) {
                    val value = BOUNDARY_BYTES[(position + index) % BOUNDARY_BYTES.size]
                    if (valid[position] != value.toByte()) yield(set(valid, position, value))
                }
            }
            for (length in valid.indices) yield(Mutant("truncate to $length", valid.copyOf(length)))
            repeat(budget(STRUCTURAL_EDITS)) { yield(structural(valid, random)) }
        }.filterNot { it.bytes.contentEquals(valid) }

    /** [count] mutants drawn at random from every family, for inputs that are expensive to process. */
    fun sampledMutants(valid: ByteArray, random: Random, count: Int): Sequence<Mutant> = sequence {
        repeat(budget(count)) {
            val position = random.nextInt(valid.size)
            yield(
                when (random.nextInt(10)) {
                    in 0..5 -> flip(valid, position, random.nextInt(8))
                    6, 7 -> set(valid, position, BOUNDARY_BYTES[random.nextInt(BOUNDARY_BYTES.size)])
                    8 -> Mutant("truncate to $position", valid.copyOf(position))
                    else -> structural(valid, random)
                },
            )
        }
    }.filterNot { it.bytes.contentEquals(valid) }

    /** Random byte strings, mostly short, every 50th of [maxLength] bytes. */
    fun randomInputs(random: Random, count: Int, maxLength: Int): Sequence<Mutant> = sequence {
        for (index in 0 until budget(count)) {
            val length = when {
                index % 50 == 49 -> maxLength
                index % 5 == 4 -> random.nextInt(maxLength + 1)
                else -> random.nextInt(minOf(maxLength, 256) + 1)
            }
            yield(Mutant("random input $index of length $length", ByteArray(length).also(random::nextBytes)))
        }
    }

    private fun flip(valid: ByteArray, position: Int, bit: Int) =
        Mutant("flip bit $bit of byte $position", valid.copyOf().also { it[position] = (it[position].toInt() xor (1 shl bit)).toByte() })

    private fun set(valid: ByteArray, position: Int, value: Int) =
        Mutant("byte $position = $value", valid.copyOf().also { it[position] = value.toByte() })

    private fun structural(valid: ByteArray, random: Random): Mutant {
        val position = random.nextInt(valid.size + 1)
        return when (random.nextInt(6)) {
            0 -> {
                val extra = ByteArray(1 + random.nextInt(16)).also(random::nextBytes)
                Mutant("insert ${extra.size} bytes at $position", valid.copyOfRange(0, position) + extra + valid.copyOfRange(position, valid.size))
            }
            1 -> {
                val end = minOf(valid.size, position + 1 + random.nextInt(16))
                Mutant("delete $position until $end", valid.copyOfRange(0, position) + valid.copyOfRange(end, valid.size))
            }
            2 -> Mutant("append ${1 + position % 16} bytes", valid + ByteArray(1 + position % 16).also(random::nextBytes))
            3 -> Mutant("append a copy of itself", valid + valid)
            else -> {
                val width = if (random.nextBoolean()) 2 else 4
                val start = random.nextInt(maxOf(1, valid.size - width + 1))
                val value = BOUNDARY_INTS[random.nextInt(BOUNDARY_INTS.size)]
                Mutant(
                    "write $value as $width bytes at $start",
                    valid.copyOf().also { bytes ->
                        for (offset in 0 until width) {
                            if (start + offset < bytes.size) bytes[start + offset] = (value ushr (8 * (width - 1 - offset))).toByte()
                        }
                    },
                )
            }
        }
    }
}

/**
 * Feeds every input to [decode]. A decoder of untrusted bytes may only reject with [allowed]; any
 * other exception or error fails the test with the input's description. [onAccepted] checks each
 * input that was accepted (for example, that it re-encodes to exactly the same bytes). Returns how
 * many inputs were tried.
 */
fun <T> decodeEach(
    inputs: Sequence<Mutant>,
    allowed: KClass<out Throwable>,
    decode: (ByteArray) -> T,
    onAccepted: (Mutant, T) -> Unit = { _, _ -> },
): Int {
    var tried = 0
    for (input in inputs) {
        tried++
        val result = try {
            decode(input.bytes)
        } catch (failure: Throwable) {
            if (allowed.isInstance(failure)) continue
            fail("${input.description}: unexpected ${failure::class.java.name} instead of ${allowed.simpleName}")
            throw failure
        }
        onAccepted(input, result)
    }
    return tried
}
