package dev.gad.intellij.lang

import com.google.gson.JsonParser
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Auto-completion for Gad, driven by the `gad complete` language service: the
 * plugin sends the editor buffer and the caret's byte offset, and the CLI returns
 * the candidates — in-scope identifiers, keywords, builtins and, for member
 * access (`x.`), the receiver's real members — each with documentation. The
 * plugin only renders them, so completion matches every other editor.
 */
class GadCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val file = parameters.originalFile
                    if (!GadFile.isGadFile(file.virtualFile)) return

                    val text = parameters.editor.document.charsSequence.toString()
                    val byteOffset = GadCli.byteOffset(text, parameters.offset)
                    val out = GadCli.run(text, "complete", "--offset", byteOffset.toString()) ?: return

                    for (item in parseItems(out)) {
                        var element = LookupElementBuilder.create(item.label)
                            .withTypeText(item.kind, true)
                        val firstLine = item.doc.lineSequence().firstOrNull { it.isNotBlank() }
                        if (firstLine != null) {
                            element = element.withTailText("  ${firstLine.trim().removePrefix("# ")}", true)
                        }
                        result.addElement(element)
                    }
                }
            },
        )
    }

    private data class Item(val label: String, val kind: String, val doc: String)

    /** Parse the `gad complete` JSON array; returns empty on any malformed input. */
    private fun parseItems(json: String): List<Item> {
        return try {
            JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
                val o = el.asJsonObject
                val label = o.get("label")?.asString ?: return@mapNotNull null
                Item(
                    label = label,
                    kind = o.get("kind")?.asString ?: "",
                    doc = o.get("doc")?.asString ?: "",
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
