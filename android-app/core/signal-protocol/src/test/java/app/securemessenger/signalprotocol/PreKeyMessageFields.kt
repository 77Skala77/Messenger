package app.securemessenger.signalprotocol

import java.io.ByteArrayOutputStream

/**
 * Test-only editor for the protobuf fields of a libsignal PreKey message: one version byte, then
 * varint (IDs) and length-delimited (keys, ciphertext) fields. libsignal offers no constructor for
 * arbitrary field values, but an attacker writes the bytes directly, so the tests do too.
 */
class PreKeyMessageFields private constructor(private val version: Byte, private val fields: MutableList<Field>) {
    private class Field(val number: Int, val wireType: Int, var varint: Long, var bytes: ByteArray?)

    /** The numbers of the varint fields, in order, with their values (for checking the layout). */
    fun varints(): Map<Int, Long> = fields.filter { it.wireType == 0 }.associate { it.number to it.varint }

    /** Replaces a varint field's value (u32 on the wire, so up to 0xFFFFFFFF). */
    fun withVarint(number: Int, value: Long): PreKeyMessageFields = apply {
        val field = fields.single { it.number == number && it.wireType == 0 }
        field.varint = value
    }

    /** Replaces a length-delimited field's bytes, e.g. the claimed identity key. */
    fun withBytes(number: Int, value: ByteArray): PreKeyMessageFields = apply {
        val field = fields.single { it.number == number && it.wireType == 2 }
        field.bytes = value.copyOf()
    }

    fun encode(): ByteArray = ByteArrayOutputStream().also { out ->
        out.write(version.toInt())
        for (field in fields) {
            writeVarint(out, (field.number.toLong() shl 3) or field.wireType.toLong())
            if (field.wireType == 0) {
                writeVarint(out, field.varint)
            } else {
                val bytes = checkNotNull(field.bytes)
                writeVarint(out, bytes.size.toLong())
                out.write(bytes)
            }
        }
    }.toByteArray()

    companion object {
        /** libsignal's field numbers (checked against a real message in the tests). */
        const val PRE_KEY_ID = 1
        const val BASE_KEY = 2
        const val IDENTITY_KEY = 3
        const val SIGNED_PRE_KEY_ID = 6
        const val KYBER_PRE_KEY_ID = 7

        fun parse(message: ByteArray): PreKeyMessageFields {
            var position = 1
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    val byte = message[position++].toInt() and 0xFF
                    result = result or ((byte and 0x7F).toLong() shl shift)
                    if (byte and 0x80 == 0) return result
                    shift += 7
                }
            }
            val fields = mutableListOf<Field>()
            while (position < message.size) {
                val key = varint()
                val number = (key ushr 3).toInt()
                when (val wireType = (key and 7).toInt()) {
                    0 -> fields += Field(number, wireType, varint(), null)
                    2 -> {
                        val length = varint().toInt()
                        fields += Field(number, wireType, 0, message.copyOfRange(position, position + length))
                        position += length
                    }
                    else -> error("Unexpected wire type $wireType")
                }
            }
            return PreKeyMessageFields(message[0], fields)
        }

        private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
            var rest = value
            while (rest and 0x7F.inv().toLong() != 0L) {
                out.write(((rest and 0x7F) or 0x80).toInt())
                rest = rest ushr 7
            }
            out.write(rest.toInt())
        }
    }
}
