package dev.gad.intellij.doc

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import dev.gad.intellij.lang.GadCli
import dev.gad.intellij.lang.GadFile
import dev.gad.intellij.settings.GadSettings
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JScrollPane

/**
 * Documentation preview of the active Gad editor. It pipes the current buffer to
 * `gad doc -name <file> -html -` and shows the rendered HTML (JCEF when
 * available, else a basic Swing HTML pane) — without writing any file.
 *
 * Rendering is EVENT-DRIVEN, never on a timer: the toolbar Refresh button, once
 * when the panel is created, and automatically when the user opens or focuses a
 * DIFFERENT Gad file (a selection change) or reopens this panel. It deliberately
 * does NOT re-render on every keystroke — a continuous refresh loop once drove the
 * JCEF browser per keystroke and could freeze the whole machine — and it skips
 * work while the panel is hidden or when the file is already the one shown.
 */
class GadDocPanel(private val project: Project, parent: Disposable) {

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val fallback = JEditorPane("text/html", "").apply { isEditable = false }

    /** Path of the file currently rendered, so a repeat selection is a no-op. */
    private var lastRenderedPath: String? = null

    /** Last-seen visibility of the Gad Doc tool window, to fire on show only. */
    private var wasVisible = false

    val component: JComponent

    init {
        browser?.let { Disposer.register(parent, it) }

        // Auto-refresh is driven by IDE events (never a timer). Both are routed
        // through onFileSelected, which gates on panel visibility and dedupes by
        // file path so a switch back-and-forth or a hidden panel spawns nothing.
        val bus = project.messageBus.connect(parent)
        bus.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) =
                    onFileSelected(event.newFile)
            },
        )
        bus.subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(manager: ToolWindowManager) {
                    val visible = manager.getToolWindow(TOOL_WINDOW_ID)?.isVisible ?: false
                    // On the panel becoming visible, sync it to the active file.
                    if (visible && !wasVisible) onFileSelected(selectedGadFile())
                    wasVisible = visible
                }
            },
        )

        val panel = SimpleToolWindowPanel(true, true)
        val refresh = object : AnAction("Refresh", "Re-render this Gad file's documentation", AllIcons.Actions.Refresh) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) = refresh()
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("GadDoc", DefaultActionGroup(refresh), true)
        val content = browser?.component ?: JScrollPane(fallback)
        toolbar.targetComponent = content
        panel.toolbar = toolbar.component
        panel.setContent(content)
        component = panel

        showMessage("Open a .gad / .gadt / .gadx file and press Refresh (⟳) to render its documentation.")
        refresh()
    }

    /** Manual refresh (toolbar ⟳): always re-render the active editor's current
     *  buffer — it may have unsaved edits — or show a hint. */
    private fun refresh() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val doc: Document? = editor?.document
        val file: VirtualFile? = doc?.let { FileDocumentManager.getInstance().getFile(it) }
        if (doc == null || file == null || !GadFile.isGadFile(file)) {
            showMessage("Open a .gad / .gadt / .gadx file and press Refresh (⟳) to render its documentation.")
            return
        }
        render(file, doc.text)
    }

    /** The active editor's file, when it is a Gad file (else null). */
    private fun selectedGadFile(): VirtualFile? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        return if (GadFile.isGadFile(file)) file else null
    }

    /** Auto-refresh entry point (selection change / panel shown). Renders only a
     *  Gad file, only while this panel is visible, and only when it differs from
     *  what is already shown — so switching to a non-Gad tab keeps the last doc. */
    private fun onFileSelected(file: VirtualFile?) {
        if (file == null || !GadFile.isGadFile(file)) return
        if (ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.isVisible != true) return
        if (file.path == lastRenderedPath) return
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return
        render(file, doc.text)
    }

    /** Run `gad doc` off the EDT, then load the HTML back on the EDT. */
    private fun render(file: VirtualFile, text: String) {
        lastRenderedPath = file.path
        val base = project.basePath
        val rel = if (base != null && file.path.startsWith("$base/")) file.path.removePrefix("$base/") else file.name
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = runDoc(rel, text)
            ApplicationManager.getApplication().invokeLater({ show(html) }, ModalityState.any())
        }
    }

    /**
     * Render this file's docs as a full HTML page via `gad doc … -html -full-page`.
     * The template (doc-templates/html.gadx) owns the whole page — sidebar, search,
     * light/dark theme and PrismJS highlighting of the gad/gadx/gadt fences — so the
     * panel no longer wraps anything; it only forces the page theme to match the IDE.
     */
    private fun runDoc(name: String, text: String): String {
        // Route through GadCli.run so the doc panel shares the plugin-wide
        // concurrency gate / size cap / pipe-drain safety (a per-keystroke process
        // must never pile up — a runaway once froze the whole machine).
        val args = mutableListOf("doc", "-name", name, "-html", "-full-page")
        // Template choice (Settings ▸ Tools ▸ Gad): "standard" forces the built-in
        // template; "config" uses the workspace `.gad/doc-templates/html.gadx` (or an
        // explicit path when set).
        val settings = GadSettings.getInstance()
        if (settings.useStandardDocTemplate()) {
            args += "-std-template"
        } else {
            settings.docHtmlTemplatePath()?.let { args += listOf("-doc-template-html", it) }
        }
        val out = GadCli.run(text, *args.toTypedArray())
            ?: return errorPage("gad doc unavailable (busy, too large, or failed)")
        return themed(out)
    }

    private fun minimalPage(bodyStyle: String, body: String): String {
        val theme = ideTheme()
        return "<!doctype html><html data-theme=\"$theme\"><head><meta charset=\"utf-8\"></head>" +
            "<body style=\"$bodyStyle\">$body</body></html>"
    }

    private fun errorPage(msg: String): String =
        minimalPage("font-family:monospace;padding:16px;color:#e06c75;white-space:pre-wrap", escape(msg))

    /** "dark" or "light" from the IDE panel background's luminance (theme-agnostic,
     *  no deprecated LaF check). */
    private fun ideTheme(): String {
        val c = UIUtil.getPanelBackground()
        val lum = (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) / 255.0
        return if (lum < 0.5) "dark" else "light"
    }

    /** Force the rendered page to the IDE theme by stamping `data-theme` on <html>. */
    private fun themed(html: String): String {
        val theme = ideTheme()
        val i = html.indexOf("<html")
        if (i < 0) return html
        val end = html.indexOf('>', i)
        if (end < 0) return html
        val head = html.substring(0, end)
        if (head.contains("data-theme")) return html
        return html.substring(0, end) + " data-theme=\"$theme\"" + html.substring(end)
    }

    private fun show(html: String) {
        browser?.loadHTML(html) ?: run { fallback.text = html; fallback.caretPosition = 0 }
    }

    private fun showMessage(msg: String) =
        show(minimalPage("font-family:sans-serif;padding:16px;opacity:.7", escape(msg)))

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        /** Tool-window id, matching the `id` in plugin.xml and [GadDocAction]. */
        private const val TOOL_WINDOW_ID = "Gad Doc"
    }
}
