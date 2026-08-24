package dev.gad.intellij.lang

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.impl.FakePsiElement
import com.intellij.util.ProcessingContext

/**
 * Ctrl+Hover / Ctrl+Click for Gad files. Gad/Gadt/Gadx are TextMate-backed, so a
 * file is one plain-text PSI leaf: [GadGotoDeclarationHandler] alone navigates,
 * but the platform then underlines that whole leaf on hover (the reported
 * "underlines the whole file"). This contributor adds a reference for the single
 * identifier under the caret, so — when it resolves — the hover highlight is a
 * tight word underline.
 *
 * It is registered ALONGSIDE the goto handler: if a reference resolves the
 * platform uses its (tight) range; if it does not, the goto handler still
 * navigates (the previous behavior). So this can only narrow the underline, never
 * break navigation.
 */
class GadReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(), GadIdentifierReferenceProvider())
    }
}

private class GadIdentifierReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        return try {
            // Gad files are TextMate-backed with no ParserDefinition, so their PSI
            // is a PsiPlainTextFile — which is the HintedReferenceHost. References
            // must be contributed on the FILE element (whose text is the whole
            // buffer and whose textRange starts at 0), not on a leaf; the platform
            // walks up to the file and matches a reference whose range contains the
            // caret, giving a tight word underline.
            if (element !is PsiFile) return PsiReference.EMPTY_ARRAY
            if (!GadFile.isGadFile(element.virtualFile)) return PsiReference.EMPTY_ARRAY
            val text = element.text ?: return PsiReference.EMPTY_ARRAY
            if (text.length < 2) return PsiReference.EMPTY_ARRAY

            val refs = ArrayList<PsiReference>()
            for (range in identifierRanges(text)) {
                refs.add(GadIdentifierReference(element, range))
            }
            if (refs.isEmpty()) PsiReference.EMPTY_ARRAY else refs.toTypedArray()
        } catch (e: Exception) {
            PsiReference.EMPTY_ARRAY
        }
    }

    /** Word ranges (`[A-Za-z_$][A-Za-z0-9_$]*`) in text, relative to the element. */
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
 * A soft reference over one identifier range. [resolve] asks `gad def` for the
 * declaration offset (using the identifier's own offset as the caret) and returns
 * a navigable target opening the file there, or null when nothing resolves — in
 * which case the goto handler takes over. Fully guarded: never throws.
 */
private class GadIdentifierReference(
    element: PsiElement,
    rangeInElement: TextRange,
) : PsiReferenceBase<PsiElement>(element, rangeInElement, /* soft = */ true) {

    override fun resolve(): PsiElement? {
        return try {
            val file = element.containingFile ?: return null
            val text = file.viewProvider.contents.toString()
            val caret = element.textRange.startOffset + rangeInElement.startOffset
            val byteOffset = GadCli.byteOffset(text, caret)
            val name = file.virtualFile?.name ?: "buffer.gad"
            val out = GadCli.run(text, "def", "--offset", byteOffset.toString(), "--stdin-name", name)
                ?: return null
            val declByteOffset = parseOffset(out) ?: return null
            val declCharOffset = charOffset(text, declByteOffset)
            // A caret already on the declaration is not a navigable target.
            if (declCharOffset == caret) return null
            GadNavTarget(element, file, declCharOffset)
        } catch (e: Exception) {
            null
        }
    }

    override fun getVariants(): Array<Any> = emptyArray()

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
