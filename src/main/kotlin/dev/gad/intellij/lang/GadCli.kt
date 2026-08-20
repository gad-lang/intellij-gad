package dev.gad.intellij.lang

import com.intellij.execution.configurations.GeneralCommandLine
import dev.gad.intellij.settings.GadExecutable
import dev.gad.intellij.settings.GadSettings
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Thin bridge to the `gad` language-service commands (`def`, `complete`). Each
 * call feeds the editor buffer to the CLI on stdin (so unsaved edits are honored)
 * and returns its stdout. The engine lives in gad — the plugin only forwards the
 * caret and renders the JSON result — so navigation and completion stay identical
 * across editors.
 */
object GadCli {

    /** UTF-8 byte offset of a character offset in text (the CLI wants bytes). */
    fun byteOffset(text: CharSequence, charOffset: Int): Int {
        val end = charOffset.coerceIn(0, text.length)
        return text.subSequence(0, end).toString().toByteArray(StandardCharsets.UTF_8).size
    }

    /**
     * Run `gad <args> -` with [text] on stdin, returning stdout, or null when the
     * executable is missing or the call fails / times out. Never throws.
     */
    fun run(text: String, vararg args: String): String? {
        val exe = GadExecutable.resolvePath(GadSettings.getInstance().gadPath) ?: return null
        return try {
            val cmd = GeneralCommandLine(mutableListOf(exe, *args, "-"))
                .withCharset(StandardCharsets.UTF_8)
            val process = cmd.createProcess()
            process.outputStream.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
            val out = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    private const val TIMEOUT_SECONDS = 5L
}
