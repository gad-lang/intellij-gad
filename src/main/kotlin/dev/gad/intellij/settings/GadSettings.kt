package dev.gad.intellij.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import java.io.File

/** Application-level Gad settings (the `gad` binary location, default GADPATH). */
@State(name = "GadSettings", storages = [Storage("gad.xml")])
class GadSettings : SimplePersistentStateComponent<GadSettings.MyState>(MyState()) {

    class MyState : BaseState() {
        /** Path to the `gad` executable; blank means "resolve from PATH". */
        var gadPath by string("")

        /** Default module search path (GADPATH), OS path-list separated. */
        var gadPathEnv by string("")

        // Formatting options — the `gad fmt` toggles. Defaults match `gad fmt`'s own
        // defaults (column-aware wrapping, nothing forced, no backup). Each
        // *NewLine toggle forces that construct onto separate lines; fmtFormat
        // forces the full multi-line layout.
        var fmtFormat by property(false)
        var fmtArrayItemNewLine by property(false)
        var fmtCallParamsNewLine by property(false)
        var fmtDeclItemNewLine by property(false)
        var fmtDictItemNewLine by property(false)
        var fmtKeyValueArrayItemNewLine by property(false)
        var fmtParamValuesNewLine by property(false)
        var fmtMaxColumns by property(0) // 0 uses gad's default column budget
        var fmtBackup by property(false)

        // Gad doc panel: which HTML template renders the preview. "standard" forces
        // the built-in template; "config" uses the workspace/config template
        // (.gad/doc-templates/html.gadx, or docHtmlTemplate below when set).
        var docTemplate by string("standard")

        /** Explicit HTML doc template path for "config" mode; blank = workspace default. */
        var docHtmlTemplate by string("")
    }

    var gadPath: String
        get() = state.gadPath.orEmpty()
        set(value) { state.gadPath = value }

    var gadPathEnv: String
        get() = state.gadPathEnv.orEmpty()
        set(value) { state.gadPathEnv = value }

    // Formatting options.
    var fmtFormat: Boolean
        get() = state.fmtFormat
        set(value) { state.fmtFormat = value }
    var fmtArrayItemNewLine: Boolean
        get() = state.fmtArrayItemNewLine
        set(value) { state.fmtArrayItemNewLine = value }
    var fmtCallParamsNewLine: Boolean
        get() = state.fmtCallParamsNewLine
        set(value) { state.fmtCallParamsNewLine = value }
    var fmtDeclItemNewLine: Boolean
        get() = state.fmtDeclItemNewLine
        set(value) { state.fmtDeclItemNewLine = value }
    var fmtDictItemNewLine: Boolean
        get() = state.fmtDictItemNewLine
        set(value) { state.fmtDictItemNewLine = value }
    var fmtKeyValueArrayItemNewLine: Boolean
        get() = state.fmtKeyValueArrayItemNewLine
        set(value) { state.fmtKeyValueArrayItemNewLine = value }
    var fmtParamValuesNewLine: Boolean
        get() = state.fmtParamValuesNewLine
        set(value) { state.fmtParamValuesNewLine = value }
    var fmtMaxColumns: Int
        get() = state.fmtMaxColumns
        set(value) { state.fmtMaxColumns = value }
    var fmtBackup: Boolean
        get() = state.fmtBackup
        set(value) { state.fmtBackup = value }

    // Gad doc panel template choice.
    var docTemplate: String
        get() = state.docTemplate.orEmpty().ifEmpty { "standard" }
        set(value) { state.docTemplate = value }
    var docHtmlTemplate: String
        get() = state.docHtmlTemplate.orEmpty()
        set(value) { state.docHtmlTemplate = value }

    /** True when the doc panel should force the built-in (standard) template. */
    fun useStandardDocTemplate(): Boolean = docTemplate != "config"

    /** The explicit HTML doc template path to pass in "config" mode, or null to let
     *  the CLI resolve the workspace `.gad/doc-templates/html.gadx`. */
    fun docHtmlTemplatePath(): String? =
        if (useStandardDocTemplate()) null else docHtmlTemplate.trim().ifEmpty { null }

    /** The `gad fmt` flags implied by the current formatting options. */
    fun fmtFlags(): List<String> = buildList {
        if (fmtFormat) add("-format")
        if (fmtArrayItemNewLine) add("-array-item-in-new-line")
        if (fmtCallParamsNewLine) add("-call-params-in-new-line")
        if (fmtDeclItemNewLine) add("-decl-item-in-new-line")
        if (fmtDictItemNewLine) add("-dict-item-in-new-line")
        if (fmtKeyValueArrayItemNewLine) add("-key-value-array-item-in-new-line")
        if (fmtParamValuesNewLine) add("-parem-values-in-new-line")
        if (fmtMaxColumns > 0) { add("-max-columns"); add(fmtMaxColumns.toString()) }
        if (fmtBackup) add("-backup")
    }

    /** The resolved `gad` executable to launch (falls back to a bare `gad`). */
    fun resolveExecutable(): String {
        val configured = gadPath.trim()
        if (configured.isNotEmpty()) return configured
        return findOnPath() ?: DEFAULT_EXE
    }

    private fun findOnPath(): String? {
        val path = System.getenv("PATH") ?: return null
        val sep = File.pathSeparatorChar
        return path.split(sep).asSequence()
            .map { File(it, DEFAULT_EXE) }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }

    companion object {
        val DEFAULT_EXE: String = if (SystemInfo.isWindows) "gad.exe" else "gad"

        @JvmStatic
        fun getInstance(): GadSettings =
            ApplicationManager.getApplication().getService(GadSettings::class.java)
    }
}
