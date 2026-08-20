package dev.gad.intellij.lang

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

/**
 * Best-effort Ctrl+Click (Go to Declaration) for Gad. The plugin has no PSI or
 * parser — highlighting comes from a TextMate grammar — so this resolves the
 * identifier under the caret by scanning the file text for a plausible
 * declaration (`name :=`, a `var`/`const`/`global`/`param` declaration, a named
 * `func`, a loop variable, or a closure/func parameter). It is single-file and
 * heuristic: it returns null (no navigation, same as before) when nothing
 * matches, so it only ever improves navigation.
 */
class GadGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val file = source?.containingFile ?: return null
        if (!GadFile.isGadFile(file.virtualFile)) return null
        val text = editor?.document?.charsSequence ?: file.viewProvider.contents
        val name = identifierAt(text, offset) ?: return null
        val declOffset = findDeclaration(text, name, offset) ?: return null
        val target = file.findElementAt(declOffset) ?: return null
        return arrayOf(target)
    }

    /** Extracts the identifier word spanning [offset], or null. */
    private fun identifierAt(text: CharSequence, offset: Int): String? {
        if (offset !in 0..text.length) return null
        fun ident(c: Char) = c == '_' || c.isLetterOrDigit()
        var start = offset
        var end = offset
        while (start > 0 && ident(text[start - 1])) start--
        while (end < text.length && ident(text[end])) end++
        if (start >= end) return null
        val word = text.subSequence(start, end).toString()
        return if (word.isEmpty() || word[0].isDigit()) null else word
    }

    /**
     * Returns the offset of a likely declaration of [name]. Patterns are tried in
     * priority order; for the first that matches it prefers the nearest
     * declaration before the caret (declaration-before-use), else the first match.
     */
    private fun findDeclaration(text: CharSequence, name: String, caret: Int): Int? {
        val n = Regex.escape(name)
        val patterns = listOf(
            Regex("""\b($n)\s*:="""),                                    // short var decl
            Regex("""\b(?:var|const|global|param)\b[^\n=]*?\b($n)\b"""),  // var/const/... declaration
            Regex("""\bfunc\s+($n)\b"""),                                 // named func
            Regex("""\bfor\b[^\n{]*?\b($n)\b\s*(?:,|:?=|\bin\b)"""),      // loop variable
            Regex("""\([^)\n]*\b($n)\b[^)\n]*\)\s*(?:=>|\{)"""),          // closure/func parameter
        )
        for (re in patterns) {
            var firstAny: Int? = null
            var beforeCaret: Int? = null
            for (m in re.findAll(text)) {
                val pos = m.groups[1]?.range?.first ?: continue
                if (firstAny == null) firstAny = pos
                if (pos < caret) beforeCaret = pos
            }
            (beforeCaret ?: firstAny)?.let { return it }
        }
        return null
    }
}
