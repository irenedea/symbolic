package symbolic

import java.lang.IllegalStateException

enum class TokenType {
    CONST,
    VAR,
    MULTIPLICATIVE,
    ADDITIVE,
    RPAR,
    LPAR,
    EOF
}

sealed class Token(val name: String, val type: TokenType) {

    class CONST(value: Int): Token(value.toString(), TokenType.CONST)
    class VAR(name: String): Token(name, TokenType.VAR)

    object LPAR: Token("(", TokenType.LPAR)
    object RPAR: Token(")", TokenType.RPAR)

    object EOF: Token("EOF", TokenType.EOF)

    sealed class OP(name: String, type: TokenType): Token(name, type) {
        object ADD: OP("+", TokenType.ADDITIVE)
        object SUB: OP("-", TokenType.ADDITIVE)
        object MUL: OP("*", TokenType.MULTIPLICATIVE)
        object DIV: OP("/", TokenType.MULTIPLICATIVE)
    }

    override fun toString(): String {
        return name
    }

    companion object {
        fun fromString(str: String): Token = when(str) {
            "+" -> OP.ADD
            "-" -> OP.SUB
            "*" -> OP.MUL
            "/" -> OP.DIV
            "(" -> LPAR
            ")" -> RPAR
            else -> {
                val maybeInt = str.toIntOrNull()
                if (maybeInt != null) CONST(maybeInt) else VAR(str)
            }
        }
    }
}

fun Char.isOpOrParens() = when (this) {
    '+','-','*','/','(',')'-> true
    else -> false
}

class Tokens {
    private val tokens: List<Token>
    private val length: Int

    constructor(input: String) {
        tokens = tokenize(input) + listOf(Token.EOF)
        length = tokens.size
    }
    constructor(_tokens: List<Token>) {
        tokens = _tokens + if (_tokens.last() != Token.EOF) listOf(Token.EOF) else listOf()
        length = tokens.size
    }

    private var index = 0

    private fun tokenize(str: String): List<Token> {
        val noSpaces = str.replace(" ", "")
        val strings: MutableList<String> = mutableListOf()
        var curr: String = ""
        for (c in noSpaces) {
            if (c.isOpOrParens()) {
                if (curr != "") {
                    strings.add(curr)
                    curr = ""
                }
                strings.add(c.toString())
            } else {
                curr += c
            }
        }
        if (curr != "") strings.add(curr)
        return strings.map { Token.fromString(it) }
    }

    fun advance(): Token {
        if (!canAdvance()) throw IllegalStateException("Cannot advance to next token for $tokens.")
        return tokens[index].also { index++ }
    }

    fun canAdvance() = index < length - 1

    fun at(type: TokenType): Boolean {
        if (index > length - 1) throw IndexOutOfBoundsException("Not at valid token index for tokens of length $length and index $index.")
        return tokens[index].type == type
    }

    fun nextAt(type: TokenType): Boolean {
        if (index + 1 < length) return false
        return tokens[index + 1].type == type
    }

    fun advancePastParensExpression(): Tokens  {
        assert(at(TokenType.LPAR))
        val startIndex = index
        var balance = -1
        advance() // LPAR
        while(canAdvance() && balance != 0) {
            if (at(TokenType.LPAR)) balance -= 1
            else if (at(TokenType.RPAR)) balance += 1
            advance()
        }
        return Tokens(tokens.subList(startIndex + 1, index - 1))
    }

    override fun toString(): String {
        return tokens.toString()
    }
}

object Lexer {
    fun tokenize(input: String): Tokens {
        return Tokens(input)
    }
}

