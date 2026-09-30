package app.securemessenger.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No production source in any module may write to the log or to standard output (`AGENTS.md`:
 * logs must never carry plaintext, keys, ciphertext, or full queue IDs). The app has no logging at
 * all, so any logging call is a finding. Replaces the check that lived in the removed `:core:crypto`.
 * JVM only: it reads the repository's source files, which do not exist on a device.
 */
class SecurityLoggingPolicyTest {
    private val androidApp = File("../..").canonicalFile

    @Test
    fun noProductionSourceLogsOrPrints() {
        val roots = listOf(File(androidApp, "app/src/main")) +
            File(androidApp, "core").listFiles().orEmpty().filter { it.isDirectory }.map { File(it, "src/main") }
        val sources = roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList() }

        assertTrue("source discovery found too little", sources.size >= 30)
        assertTrue(sources.any { it.name == "ConversationV2Service.kt" })
        assertTrue(sources.any { it.name == "EncryptedSignalStoreRepository.kt" })
        val findings = sources.flatMap { source ->
            val text = source.readText()
            FORBIDDEN.filter { it.containsMatchIn(text) }.map { "${source.relativeTo(androidApp)}: ${it.pattern}" }
        }
        assertTrue("logging or printing in production sources: $findings", findings.isEmpty())
    }

    private companion object {
        val FORBIDDEN = listOf(
            Regex("""android\.util\.Log\b"""),
            Regex("""\bLog\.(v|d|i|w|e|wtf)\("""),
            Regex("""\bprintln\("""),
            Regex("""\bprint\("""),
            Regex("""printStackTrace\("""),
            Regex("""System\.(out|err)\b"""),
            Regex("""\bTimber\b"""),
        )
    }
}
