package dev.gad.intellij.lang

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.ProcessingContext

/**
 * Ctrl+Hover / Ctrl+Click reference for Gad. Gad files are TextMate-backed (no
 * fine-grained PSI), so the whole file is a single leaf element: the previous
 * GotoDeclarationHandler let the platform underline that whole element on hover.
 *
 * Here a reference is contributed for the *identifier under the caret only* —
 * computed by scanning the file text around the reference offset — so the hover
 * highlight is a tight word underline, matching how `.go` and other real-PSI
 * languages behave. Resolution still delegates to the `gad def` scope resolver.
 */
class GadReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            GadIdentifierReferenceProvider(),
        )
    }
}

private class GadIdentifierReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
        if (!GadFile.isGadFile(file.virtualFile)) return PsiReference.EMPTY_ARRAY
        // Contribute references on the leaf element only (no children), so they are
        // not duplicated on the enclosing file/synthetic nodes.
        if (element.firstChild != null) return PsiReference.EMPTY_ARRAY
        val text = element.text ?: return PsiReference.EMPTY_ARRAY
        if (text.length < 2) return PsiReference.EMPTY_ARRAY

        val refs = ArrayList<PsiReference>()
        for (range in identifierRanges(text)) {
            refs.add(GadIdentifierReference(element, range))
        }
        return if (refs.isEmpty()) PsiReference.EMPTY_ARRAY else refs.toTypedArray()
    }

    /**
     * Word ranges (identifiers) in text, relative to the element start. An
     * identifier is `[A-Za-z_$][A-Za-z0-9_$]*` not preceded by `.` (so member
     * accesses resolve as a whole is left to the resolver) and not a numeric.
     * Keeping every identifier a candidate keeps the reference regions tight; the
     * resolver returns null for the ones that do not resolve (keywords, builtins),
     * which the platform then leaves un-highlighted.
     */
    private fun identifierRanges(text: String): List<TextRange> {
        val out = ArrayList<TextRange>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (c.isLetter() || c == '_' || c == '$') {
                val start = i
                i++
                while (i < n) {
                    val d = text[i]
                    if (d.isLetterOrDigit() || d == '_' || d == '$') i++ else break
                }
                out.add(TextRange(start, i))
            } else {
                i++
            }
        }
        return out
    }
}

/**
 * A soft reference over one identifier range. `resolve()` asks `gad def` for the
 * declaration offset (using the identifier's own offset as the caret) and, if
 * found, returns a navigable target that opens the file there.
 */
private class GadIdentifierReference(
    element: PsiElement,
    rangeInElement: TextRange,
) : PsiPolyVariantReferenceBase<PsiElement>(element, rangeInElement, /* soft = */ true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val file = element.containingFile ?: return ResolveResult.EMPTY_ARRAY
        val text = file.viewProvider.contents.toString()
        // Absolute caret offset: the element start plus the range start.
        val caret = element.textRange.startOffset + rangeInElement.startOffset
        val byteOffset = GadCli.byteOffset(text, caret)
        val name = file.virtualFile?.name ?: "buffer.gad"
        val out = GadCli.run(text, "def", "--offset", byteOffset.toString(), "--stdin-name", name)
            ?: return ResolveResult.EMPTY_ARRAY
        val declByteOffset = parseOffset(out) ?: return ResolveResult.EMPTY_ARRAY
        val declCharOffset = charOffset(text, declByteOffset)
        // A self-reference (the caret is already on the declaration) is not a
        // navigable target — leave it unresolved so it is not underlined.
        if (declCharOffset == caret) return ResolveResult.EMPTY_ARRAY
        val target = GadNavTarget(element, file, declCharOffset)
        return arrayOf(object : ResolveResult {
            override fun getElement(): PsiElement = target
            override fun isValidResult(): Boolean = true
        })
    }

    private fun parseOffset(json: String): Int? {
        val m = Regex("\"offset\"\\s*:\\s*(\\d+)").find(json) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    private fun charOffset(text: String, byteOffset: Int): Int {
        var bytes = 0
        for (i in text.indices) {
            if (bytes >= byteOffset) return i
            bytes += text[i].toString().toByteArray(Charsets.UTF_8).size
        }
        return text.length
    }
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
