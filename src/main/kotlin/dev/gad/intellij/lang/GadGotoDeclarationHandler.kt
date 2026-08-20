package dev.gad.intellij.lang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

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
        val out = GadCli.run(text, "def", "--offset", byteOffset.toString()) ?: return null

        val declByteOffset = parseOffset(out) ?: return null
        val declCharOffset = charOffset(text, declByteOffset)
        val target = file.findElementAt(declCharOffset) ?: return null
        return arrayOf(target)
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
