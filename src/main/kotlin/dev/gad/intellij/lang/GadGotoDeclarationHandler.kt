package dev.gad.intellij.lang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement

/**
 * Ctrl+Click (Go to Declaration) for Gad, resolved by the `gad def` language
 * service: the plugin sends the editor buffer and the caret's byte offset to the
 * CLI, which returns the declaration's offset using real scope resolution
 * (blocks, functions, shadowing). The plugin then navigates there. Returns null
 * (no navigation) when the binary is missing or nothing resolves, so it only ever
 * improves navigation.
 */
class GadGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val file = source?.containingFile ?: return null
        if (!GadFile.isGadFile(file.virtualFile)) return null
        val text = (editor?.document?.charsSequence ?: file.viewProvider.contents).toString()

        val byteOffset = GadCli.byteOffset(text, offset)
        // Pass the file name so `gad def` picks the dialect (.gad / .gadx).
        val name = file.virtualFile?.name ?: "buffer.gad"
        val out = GadCli.run(text, "def", "--offset", byteOffset.toString(), "--stdin-name", name)
            ?: return null

        val declByteOffset = parseOffset(out) ?: return null
        val declCharOffset = charOffset(text, declByteOffset)
        // Navigate to the exact declaration offset. Gad/Gadx files are TextMate
        // (no fine-grained PSI), so file.findElementAt returns the whole-file
        // element whose navigation lands at offset 0 — hence a FakePsiElement that
        // opens the file at declCharOffset.
        val base = file.findElementAt(offset) ?: file
        return arrayOf(GadNavTarget(base, file, declCharOffset))
    }

    /** A navigable target that opens [file] at [offset] (exact caret placement). */
    private class GadNavTarget(
        private val anchor: PsiElement,
        private val file: PsiFile,
        private val offset: Int,
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = anchor
        override fun getContainingFile(): PsiFile = file
        override fun getProject(): Project = file.project
        override fun getTextOffset(): Int = offset
        override fun getName(): String? = null
        override fun canNavigate(): Boolean = file.virtualFile != null
        override fun navigate(requestFocus: Boolean) {
            val vf = file.virtualFile ?: return
            OpenFileDescriptor(file.project, vf, offset).navigate(requestFocus)
        }
    }

    /** Extract the `"offset"` field from the `gad def` JSON, or null for `null`. */
    private fun parseOffset(json: String): Int? {
        val m = Regex("\"offset\"\\s*:\\s*(\\d+)").find(json) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    /** Convert a UTF-8 byte offset back to a character offset in text. */
    private fun charOffset(text: String, byteOffset: Int): Int {
        var bytes = 0
        for (i in text.indices) {
            if (bytes >= byteOffset) return i
            bytes += text[i].toString().toByteArray(Charsets.UTF_8).size
        }
        return text.length
    }
}
