package dev.gad.intellij.lang

import com.intellij.execution.configurations.GeneralCommandLine
import dev.gad.intellij.settings.GadExecutable
import dev.gad.intellij.settings.GadSettings
import java.nio.charset.StandardCharsets
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Thin bridge to the `gad` language-service commands (`def`, `complete`, `doc`).
 * Each call feeds the editor buffer to the CLI on stdin (so unsaved edits are
 * honored) and returns its stdout. The engine lives in gad — the plugin only
 * forwards the caret and renders the JSON result — so navigation and completion
 * stay identical across editors.
 *
 * Because completion and the live doc panel invoke this PER KEYSTROKE, it is
 * bounded so it can never overwhelm the machine (a runaway once froze the whole
 * OS): a small semaphore caps how many `gad` processes run at once — extra calls
 * return null immediately instead of piling up — a size cap skips very large
 * buffers, and each process is force-killed on timeout with its pipes drained on
 * separate threads (so a full stdout/stderr pipe can never deadlock the read).
 */
object GadCli {

    /** UTF-8 byte offset of a character offset in text (the CLI wants bytes). */
    fun byteOffset(text: CharSequence, charOffset: Int): Int {
        val end = charOffset.coerceIn(0, text.length)
        return text.subSequence(0, end).toString().toByteArray(StandardCharsets.UTF_8).size
    }

    // At most this many `gad` processes run concurrently across the whole plugin.
    // Fairness keeps callers from starving; a call that cannot get a permit
    // immediately gives up (returns null) rather than queueing up work per
    // keystroke.
    private val gate = Semaphore(MAX_CONCURRENT, /* fair = */ true)

    /**
     * Run `gad <args> -` with [text] on stdin, returning stdout, or null when the
     * executable is missing, the buffer is too large, the concurrency gate is
     * full, or the call fails / times out. Never throws, never blocks unbounded.
     */
    fun run(text: String, vararg args: String): String? {
        // Skip pathologically large buffers — language-service latency there is not
        // worth the resource risk while typing.
        if (text.length > MAX_INPUT_CHARS) return null
        val exe = GadExecutable.resolvePath(GadSettings.getInstance().gadPath) ?: return null

        // Bound concurrency: if too many gad processes are already running, skip
        // this call instead of adding another (prevents per-keystroke pile-up).
        if (!gate.tryAcquire(ACQUIRE_WAIT_MS, TimeUnit.MILLISECONDS)) return null

        var process: Process? = null
        return try {
            val cmd = GeneralCommandLine(mutableListOf(exe, *args, "-"))
                .withCharset(StandardCharsets.UTF_8)
            process = cmd.createProcess()
            val p = process

            val stdin = Thread {
                try {
                    p.outputStream.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }
            val stderr = Thread {
                try {
                    p.errorStream.use { it.readBytes() }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }

            val out = p.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }

            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return null
            }
            stdin.join(500)
            stderr.join(500)
            if (p.exitValue() != 0) null else out
        } catch (e: Exception) {
            null
        } finally {
            try {
                if (process?.isAlive == true) process.destroyForcibly()
            } catch (_: Exception) {
            }
            gate.release()
        }
    }

    private const val TIMEOUT_SECONDS = 5L
    private const val MAX_CONCURRENT = 2
    private const val ACQUIRE_WAIT_MS = 150L
    private const val MAX_INPUT_CHARS = 2_000_000
}
