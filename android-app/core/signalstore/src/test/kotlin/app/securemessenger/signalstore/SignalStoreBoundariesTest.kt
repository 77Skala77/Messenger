package app.securemessenger.signalstore

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SignalStoreBoundariesTest {
    @Test
    fun `addresses reject blank names and device IDs libsignal would reject`() {
        assertEquals(1, SignalProtocolAddress("contact", 1).deviceId)
        assertEquals(127, SignalProtocolAddress("contact", 127).deviceId)
        listOf(0, 128, -1).forEach { deviceId ->
            assertThrows(IllegalArgumentException::class.java) { SignalProtocolAddress("contact", deviceId) }
        }
        assertThrows(IllegalArgumentException::class.java) { SignalProtocolAddress(" ", 1) }
    }

    @Test
    fun `local identity records reject empty keys and non-positive registration IDs`() {
        assertThrows(IllegalArgumentException::class.java) { SignalLocalIdentityRecord(0, byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { SignalLocalIdentityRecord(5, ByteArray(0)) }
        assertEquals(5, SignalLocalIdentityRecord(5, byteArrayOf(1)).registrationId)
    }

    @Test
    fun `signalstore sources contain no logging calls`() {
        File("src/main/kotlin").walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { source ->
                val content = source.readText()
                assertFalse(source.path, content.contains("android.util.Log"))
                assertFalse(source.path, Regex("""\bprintln\(""").containsMatchIn(content))
                assertFalse(source.path, Regex("""\bprint\(""").containsMatchIn(content))
            }
    }
}
