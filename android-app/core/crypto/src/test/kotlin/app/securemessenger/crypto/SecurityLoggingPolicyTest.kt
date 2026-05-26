package app.securemessenger.crypto

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SecurityLoggingPolicyTest {
    @Test
    fun `crypto implementation contains no application logging calls`() {
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
