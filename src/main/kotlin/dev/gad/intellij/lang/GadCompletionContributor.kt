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
                    // Pass the file name so `gad complete` picks the dialect (.gad / .gadx).
                    val name = file.virtualFile?.name ?: "buffer.gad"
                    val out = GadCli.run(text, "complete", "--offset", byteOffset.toString(), "--stdin-name", name)
                        ?: return

                    // Match against the real identifier prefix before the caret, not
                    // the platform's auto-detected one. At a non-identifier position
                    // (e.g. `for i, u in ‸ begin`) IntelliJ derives its prefix from the
                    // inserted dummy identifier, which no candidate label matches — so
                    // an empty slot showed "No suggestions" while `u‸` worked. Computing
                    // the prefix from the original document (EDT-only, no caret-model
                    // navigation) fixes the empty case without the earlier restart loop.
                    val res = result.withPrefixMatcher(identPrefixBefore(text, parameters.offset))

                    for (item in parseItems(out)) {
                        var element = LookupElementBuilder.create(item.label)
                            .withTypeText(item.kind, true)
                        val firstLine = item.doc.lineSequence().firstOrNull { it.isNotBlank() }
                        if (firstLine != null) {
                            element = element.withTailText("  ${firstLine.trim().removePrefix("# ")}", true)
                        }
                        res.addElement(element)
                    }
                }
            },
        )
    }

    /**
     * The identifier characters immediately before [caret] in [text] (letters,
     * digits, `_`), i.e. what the user has typed of the current word. Empty when
     * the caret is not preceded by an identifier char — which is exactly when the
     * platform's own prefix would be wrong, so we set an empty matcher there and
     * every candidate is shown.
     */
    private fun identPrefixBefore(text: CharSequence, caret: Int): String {
        var start = caret.coerceIn(0, text.length)
        while (start > 0) {
            val c = text[start - 1]
            if (c.isLetterOrDigit() || c == '_') start-- else break
        }
        return text.subSequence(start, caret.coerceIn(0, text.length)).toString()
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
