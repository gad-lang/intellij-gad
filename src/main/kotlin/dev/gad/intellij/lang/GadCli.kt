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
     *
     * stdin is written and stdout + stderr are drained on separate threads so the
     * call never deadlocks: writing the whole buffer first and only then reading
     * (the previous approach) blocks forever once the child fills its stdout/stderr
     * pipe before the parent starts reading — which froze the IDE on large files
     * or verbose parse errors, since completion / the doc panel call this per
     * keystroke. stderr is drained and discarded so a full stderr pipe cannot
     * stall the child either.
     */
    fun run(text: String, vararg args: String): String? {
        val exe = GadExecutable.resolvePath(GadSettings.getInstance().gadPath) ?: return null
        return try {
            val cmd = GeneralCommandLine(mutableListOf(exe, *args, "-"))
                .withCharset(StandardCharsets.UTF_8)
            val process = cmd.createProcess()

            val stdin = Thread {
                try {
                    process.outputStream.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
            val stderr = Thread {
                try {
                    process.errorStream.use { it.readBytes() }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }

            val out = process.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            stdin.join(500)
            stderr.join(500)
            if (process.exitValue() != 0) null else out
        } catch (e: Exception) {
            null
        }
    }

    private const val TIMEOUT_SECONDS = 5L
}
