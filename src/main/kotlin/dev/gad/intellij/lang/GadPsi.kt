package dev.gad.intellij.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * Minimal PSI for the Gad languages so that per-identifier navigation works.
 *
 * Highlighting is still done by the bundled TextMate grammars (a lexer-based
 * editor highlighter, independent of PSI). This ParserDefinition exists only to
 * give the file a real, token-level PSI tree — an [IDENTIFIER] leaf per word —
 * so that `file.findElementAt(caret)` returns the *word*, and Ctrl+hover / Ctrl+
 * click underline just that word instead of the whole (previously single-leaf)
 * plain-text file. The parse tree is flat and never produces errors, so it adds
 * no red highlighting of its own.
 */

/** A Gad token type (identifier / other / whitespace). */
class GadTokenType(debugName: String) : IElementType(debugName, GadLanguage)

object GadTokens {
    /** A word: `[A-Za-z_$][A-Za-z0-9_$]*`. Navigation anchors on these. */
    @JvmField val IDENTIFIER = GadTokenType("GAD_IDENTIFIER")

    /** Any run of non-identifier, non-whitespace characters. */
    @JvmField val OTHER = GadTokenType("GAD_OTHER")
}

/** File node type for the Gad PSI. */
val GAD_FILE: IFileElementType = IFileElementType(GadLanguage)

/**
 * A flat lexer: it splits the text into IDENTIFIER words, WHITE_SPACE runs and
 * OTHER runs. It always tokenizes the entire input (no BAD_CHARACTER), so it can
 * never fail on arbitrary Gad/Gadt/Gadx source.
 */
class GadLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var start = 0
    private var end = 0
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.start = startOffset
        locate()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = type
    override fun getTokenStart(): Int = start
    override fun getTokenEnd(): Int = end
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        start = end
        locate()
    }

    private fun locate() {
        if (start >= endOffset) {
            type = null
            end = start
            return
        }
        val c = buffer[start]
        when {
            isIdentStart(c) -> {
                var i = start + 1
                while (i < endOffset && isIdentPart(buffer[i])) i++
                end = i
                type = GadTokens.IDENTIFIER
            }
            c.isWhitespace() -> {
                var i = start + 1
                while (i < endOffset && buffer[i].isWhitespace()) i++
                end = i
                type = TokenType.WHITE_SPACE
            }
            else -> {
                var i = start + 1
                while (i < endOffset && !isIdentStart(buffer[i]) && !buffer[i].isWhitespace()) i++
                end = i
                type = GadTokens.OTHER
            }
        }
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_' || c == '$'
    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
}

/** The Gad PSI file. */
class GadPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, GadLanguage) {
    override fun getFileType() = viewProvider.virtualFile.fileType
    override fun toString(): String = "Gad File"
}

/**
 * A trivial parser: it wraps every token in a single flat root node (no nesting,
 * no error recovery), which is all the identifier-level PSI navigation needs.
 */
class GadParserDefinition : ParserDefinition {
    override fun createLexer(project: Project): Lexer = GadLexer()

    override fun createParser(project: Project): PsiParser = PsiParser { root, builder ->
        val mark = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        mark.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType(): IFileElementType = GAD_FILE
    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = GadPsiFile(viewProvider)
}
