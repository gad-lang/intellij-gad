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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import dev.gad.intellij.lang.GadCli
import dev.gad.intellij.lang.GadFile
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JScrollPane

/**
 * Documentation preview of the active Gad editor. It pipes the current buffer to
 * `gad doc -name <file> -html -` and shows the rendered HTML (JCEF when
 * available, else a basic Swing HTML pane) — without writing any file.
 *
 * Rendering is ON DEMAND via the toolbar Refresh button (and once when the panel
 * is created), NOT on a timer: a continuous refresh loop drove the JCEF browser
 * on every keystroke and could freeze the whole machine.
 */
class GadDocPanel(private val project: Project, parent: Disposable) {

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val fallback = JEditorPane("text/html", "").apply { isEditable = false }

    val component: JComponent

    init {
        browser?.let { Disposer.register(parent, it) }

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

    /** Render the active Gad editor's current buffer, or show a hint. */
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

    /** Run `gad doc` off the EDT, then load the HTML back on the EDT. */
    private fun render(file: VirtualFile, text: String) {
        // The generated docs mirror the source tree under the workspace `doc/`
        // directory, and `/PATH` in doc comments resolves against that `doc/` root.
        // Pass the source's project-relative path as --name so the module identity
        // matches, and base the preview at the file's `doc/` location so relative
        // asset links resolve the same way they do in the generated docs.
        val base = project.basePath
        val rel = if (base != null && file.path.startsWith("$base/")) file.path.removePrefix("$base/") else file.name
        val docBase = if (base != null) "file://$base/doc/${rel.substringBeforeLast('/', "")}".trimEnd('/') + "/" else null
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = runDoc(rel, text, docBase)
            ApplicationManager.getApplication().invokeLater({ show(html) }, ModalityState.any())
        }
    }

    private fun runDoc(name: String, text: String, docBase: String?): String {
        // Route through GadCli.run so the doc panel shares the plugin-wide
        // concurrency gate / size cap / pipe-drain safety (a per-keystroke process
        // must never pile up — a runaway once froze the whole machine). GadCli.run
        // appends the `-` stdin marker, so this is `gad doc -name NAME -html -`.
        val out = GadCli.run(text, "doc", "-name", name, "-html")
            ?: return errorHtml("gad doc unavailable (busy, too large, or failed)")
        return try {
            page(out, docBase)
        } catch (e: Exception) {
            errorHtml(e.message ?: e.toString())
        }
    }

    private fun show(html: String) {
        browser?.loadHTML(html) ?: run { fallback.text = html; fallback.caretPosition = 0 }
    }

    private fun showMessage(msg: String) = show(page("<p style='opacity:.7'>${escape(msg)}</p>", null))

    private fun errorHtml(msg: String) =
        page("<pre style='color:#e06c75;white-space:pre-wrap'>${escape(msg)}</pre>", null)

    /** Wrap an HTML fragment in a theme-aware page, basing relative URLs (asset
     * links in doc comments) at the file's `doc/` directory when known. */
    private fun page(body: String, docBase: String?): String {
        val bg = hex(UIUtil.getPanelBackground())
        val fg = hex(UIUtil.getLabelForeground())
        val link = hex(UIUtil.getLabelForeground())
        val border = hex(UIUtil.getBoundsColor())
        val mono = "SFMono-Regular, Consolas, 'Liberation Mono', Menlo, monospace"
        val baseTag = if (docBase != null) "<base href=\"$docBase\">" else ""
        return """
            <!doctype html><html><head><meta charset="utf-8">$baseTag<style>
              body { background:$bg; color:$fg; font-family:sans-serif; font-size:13px;
                     line-height:1.5; margin:0; padding:12px 16px; }
              h1 { font-size:1.5em; } h2 { font-size:1.25em; border-bottom:1px solid $border; padding-bottom:.2em; }
              h3 { font-size:1.05em; } a { color:$link; }
              code, pre { font-family:$mono; font-size:12px; }
              pre { background:rgba(127,127,127,.12); padding:8px 10px; border-radius:6px; overflow:auto; }
              code { background:rgba(127,127,127,.12); padding:1px 4px; border-radius:4px; }
              pre code { background:none; padding:0; }
              table { border-collapse:collapse; } th,td { border:1px solid $border; padding:4px 8px; }
              blockquote { margin:0; padding:0 12px; border-left:3px solid $border; opacity:.85; }
            </style></head><body>$body</body></html>
        """.trimIndent()
    }

    private fun hex(c: Color): String = "#%02x%02x%02x".format(c.red, c.green, c.blue)

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
