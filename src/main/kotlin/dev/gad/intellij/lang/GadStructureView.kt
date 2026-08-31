package dev.gad.intellij.lang

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Structure View for Gad files. Gad has no PSI parser (its files fall back to
 * plain text, language TEXT), so the outline is produced by the `gad symbols`
 * language service: it returns a JSON tree of the file's declarations — const/var,
 * func, class, mixin, interface, enum, met, with a type's own members nested — and
 * each node navigates to the declaration's byte offset.
 *
 * Registered for language TEXT (where Gad PSI actually lands) and Gad; the factory
 * filters to Gad files, so other plain-text files are unaffected.
 */
class GadStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (!GadFile.isGadFile(psiFile.virtualFile)) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                GadStructureViewModel(psiFile)

            override fun isRootNodeShown(): Boolean = false
        }
    }
}

/** One outline node parsed from `gad symbols` JSON. */
data class GadSymbol(
    val name: String,
    val kind: String,
    val detail: String,
    val offset: Int,
    val children: List<GadSymbol>,
) {
    companion object {
        fun from(el: JsonElement): GadSymbol? {
            val o = el.asJsonObject
            val name = o.get("name")?.asString ?: return null
            val kids = o.getAsJsonArray("children")?.mapNotNull { from(it) } ?: emptyList()
            return GadSymbol(
                name = name,
                kind = o.get("kind")?.asString ?: "",
                detail = o.get("detail")?.asString ?: "",
                offset = o.get("offset")?.asInt ?: 0,
                children = kids,
            )
        }

        /** Parse the `gad symbols` JSON array; returns an empty list on any error. */
        fun parse(json: String?): List<GadSymbol> = try {
            if (json.isNullOrBlank()) emptyList()
            else JsonParser.parseString(json).asJsonArray.mapNotNull { from(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class GadStructureViewModel(psiFile: PsiFile) :
    StructureViewModelBase(psiFile, GadFileElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean =
        element is GadSymbolElement && element.symbol.children.isEmpty()
}

/** The (hidden) root element: its children are the file's top-level symbols. */
class GadFileElement(private val psiFile: PsiFile) : StructureViewTreeElement {
    override fun getValue(): Any = psiFile

    override fun navigate(requestFocus: Boolean) {
        (psiFile as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (psiFile as? Navigatable)?.canNavigate() ?: false
    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = psiFile.name
            override fun getIcon(unused: Boolean): Icon? = psiFile.getIcon(0)
        }

    override fun getChildren(): Array<TreeElement> {
        val text = psiFile.viewProvider.document?.text ?: psiFile.text
        val name = psiFile.virtualFile?.name ?: "buffer.gad"
        val json = GadCli.run(text, "symbols", "--stdin-name", name)
        return GadSymbol.parse(json)
            .map { GadSymbolElement(psiFile, it) }
            .toTypedArray()
    }
}

/** One symbol node; navigates to its declaration offset. */
class GadSymbolElement(
    private val psiFile: PsiFile,
    val symbol: GadSymbol,
) : StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = symbol

    override fun getAlphaSortKey(): String = symbol.name

    override fun navigate(requestFocus: Boolean) {
        val vf = psiFile.virtualFile ?: return
        OpenFileDescriptor(psiFile.project, vf, symbol.offset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = psiFile.virtualFile != null
    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getChildren(): Array<TreeElement> =
        symbol.children.map { GadSymbolElement(psiFile, it) }.toTypedArray()

    override fun getPresentation(): ItemPresentation =
        object : ItemPresentation {
            override fun getPresentableText(): String = symbol.name
            override fun getLocationString(): String? = symbol.detail.ifBlank { null }
            override fun getIcon(unused: Boolean): Icon = iconFor(symbol.kind)
        }

    private fun iconFor(kind: String): Icon = when (kind) {
        "class" -> AllIcons.Nodes.Class
        "mixin" -> AllIcons.Nodes.AbstractClass
        "type" -> AllIcons.Nodes.Type
        "interface" -> AllIcons.Nodes.Interface
        "enum" -> AllIcons.Nodes.Enum
        "func", "met" -> AllIcons.Nodes.Function
        "method", "new" -> AllIcons.Nodes.Method
        "property" -> AllIcons.Nodes.Property
        "field" -> AllIcons.Nodes.Field
        "const" -> AllIcons.Nodes.Constant
        "var" -> AllIcons.Nodes.Variable
        "value" -> AllIcons.Nodes.Enum
        else -> AllIcons.Nodes.Tag
    }
}
