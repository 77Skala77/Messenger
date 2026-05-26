package app.securemessenger.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Hkdf {
    fun sha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        require(outputLength in 1..(255 * HASH_LENGTH))
        val extract = Mac.getInstance(ALGORITHM)
        extract.init(SecretKeySpec(salt, ALGORITHM))
        val pseudoRandomKey = extract.doFinal(inputKeyMaterial)
        try {
            val result = ByteArray(outputLength)
            var previous = ByteArray(0)
            var written = 0
            var block = 1
            while (written < outputLength) {
                val expand = Mac.getInstance(ALGORITHM)
                expand.init(SecretKeySpec(pseudoRandomKey, ALGORITHM))
                expand.update(previous)
                expand.update(info)
                expand.update(block.toByte())
                previous = expand.doFinal()
                val count = minOf(previous.size, outputLength - written)
                previous.copyInto(result, written, 0, count)
                written += count
                block += 1
            }
            previous.fill(0)
            return result
        } finally {
            pseudoRandomKey.fill(0)
        }
    }

    private const val ALGORITHM = "HmacSHA256"
    private const val HASH_LENGTH = 32
}
