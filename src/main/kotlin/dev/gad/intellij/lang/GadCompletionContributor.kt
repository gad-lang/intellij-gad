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

                    // Use the REAL editor caret + original document (not
                    // parameters.offset, which is in the completion copy that has the
                    // synthetic `IntellijIdeaRulezzz` identifier inserted — the copy's
                    // PSI is what changed when a ParserDefinition was added, and that
                    // desynced the offset / IDE-computed prefix).
                    val text = parameters.editor.document.charsSequence.toString()
                    val caret = parameters.editor.caretModel.offset
                    val byteOffset = GadCli.byteOffset(text, caret)
                    // Pass the file name so `gad complete` picks the dialect (.gad / .gadx).
                    val name = file.virtualFile?.name ?: "buffer.gad"
                    val out = GadCli.run(text, "complete", "--offset", byteOffset.toString(), "--stdin-name", name)
                        ?: return

                    // Match candidates against the identifier already typed before the
                    // caret, so the IDE does not filter them out with a prefix derived
                    // from the (now real) PSI position.
                    val prefix = identifierPrefix(text, caret)
                    val rs = if (prefix.isEmpty()) result else result.withPrefixMatcher(prefix)

                    for (item in parseItems(out)) {
                        var element = LookupElementBuilder.create(item.label)
                            .withTypeText(item.kind, true)
                        val firstLine = item.doc.lineSequence().firstOrNull { it.isNotBlank() }
                        if (firstLine != null) {
                            element = element.withTailText("  ${firstLine.trim().removePrefix("# ")}", true)
                        }
                        rs.addElement(element)
                    }
                }
            },
        )
    }

    private data class Item(val label: String, val kind: String, val doc: String)

    /** The run of identifier chars ending at [caret] (the typed completion prefix). */
    private fun identifierPrefix(text: CharSequence, caret: Int): String {
        var start = caret.coerceIn(0, text.length)
        while (start > 0) {
            val c = text[start - 1]
            if (c.isLetterOrDigit() || c == '_' || c == '$') start-- else break
        }
        return text.subSequence(start, caret.coerceIn(0, text.length)).toString()
    }

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
