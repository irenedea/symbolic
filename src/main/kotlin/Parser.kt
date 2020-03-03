package symbolic

sealed class Op(val str: String) {
    override fun toString(): String {
        return str
    }

    companion object {
        fun fromToken(token: Token): Op = when(token) {
            Token.OP.ADD -> Add
            Token.OP.SUB -> Sub
            Token.OP.MUL -> Mul
            Token.OP.DIV -> Div
            else -> throw IllegalArgumentException("Token $token is not Op")
        }
    }
}
sealed class Additive(str: String): Op(str)
sealed class Multiplicative(str: String): Op(str)
object Add: Additive("+")
object Sub: Additive("-")
object Div: Multiplicative("/")
object Mul: Multiplicative("*")

sealed class Unary(str: String): Op(str)
object Negate: Unary("-")

sealed class ASTNode {
    abstract fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode

    abstract fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode

    companion object {
        fun constOrVarFromToken(token: Token): ASTNode = when (token.type) {
            TokenType.CONST -> Const(token.name.toInt())
            TokenType.VAR -> Var(token.name)
            else -> throw IllegalArgumentException("Token $token is not Const or Var")
        }
    }
}

class UnaryCall(val op: Op, val arg: ASTNode): ASTNode() {
    override fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode {
        return UnaryCall(op, arg.acceptDistributeLeft(newOp, expr))
    }

    override fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode {
        return UnaryCall(op, arg.acceptDistributeRight(newOp, expr))
    }

    override fun toString(): String {
        return ("($op$arg)")
    }
}

class Call(val op: Op, val left: ASTNode, val right: ASTNode): ASTNode() {
    override fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode {
        if (op is Additive)
            return Call(op, left.acceptDistributeLeft(newOp, expr), right.acceptDistributeLeft(newOp, expr))
        else {
            val newExpr = Call(op, expr, left)
            return right.acceptDistributeLeft(newOp, newExpr)
        }
    }
    override fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode {
        if (op is Additive)
            return Call(op, left.acceptDistributeRight(newOp, expr), right.acceptDistributeRight(newOp, expr))
        else {
            val newExpr = Call(op, right, expr)
            return left.acceptDistributeRight(newOp, newExpr)
        }
    }


    override fun toString(): String {
        return "($left $op $right)"
    }

    override fun hashCode(): Int {
        return arrayOf(op, left, right).contentDeepHashCode()
    }
}

class FlatCall(val op: Op, val args: List<ASTNode>): ASTNode() {
    override fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode {
        return FlatCall(op, args.map { it.acceptDistributeLeft(op, expr)})
    }

    override fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    override fun toString(): String {
        return args.joinToString(" $op ")
    }

    override fun hashCode(): Int {
        return arrayOf(op, args).contentDeepHashCode()
    }
}

class Var(val name: String): ASTNode() {
    override fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode {
        return Call(newOp, expr, this)
    }

    override fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode {
        return Call(newOp, this, expr)
    }

    override fun toString(): String {
        return name
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}

class Const(val value: Int): ASTNode() {
    override fun acceptDistributeLeft(newOp: Op, expr: ASTNode): ASTNode {
        return Call(newOp, expr, this)
    }
    override fun acceptDistributeRight(newOp: Op, expr: ASTNode): ASTNode {
        return Call(newOp, this, expr)
    }
    override fun toString(): String {
        return value.toString()
    }

    override fun hashCode(): Int {
        return value
    }
}

class Parser(val tokens: Tokens) {
    private fun nextAt(type: TokenType) = tokens.nextAt(type)

    private fun at(type: TokenType) = tokens.at(type)

    private fun advance() = tokens.advance()

    private fun canAdvance() = tokens.canAdvance()

    private fun advancePastParensExpression() = tokens.advancePastParensExpression()

    fun parsePrimary(): ASTNode {
        if (at(TokenType.LPAR)) {
            return Parser(advancePastParensExpression()).parse()
        }
        return ASTNode.constOrVarFromToken(tokens.advance())
    }

    fun parseUnary(): ASTNode {
        if (at(TokenType.MINUS)) {
            val op = Op.fromToken(advance())
            val arg = parsePrimary()
            return UnaryCall(op, arg)
        }
        return parsePrimary()
    }

    fun parseMultiplicative(): ASTNode {
        var left: ASTNode = parseUnary()

        while(at(TokenType.TIMES) || at(TokenType.DIV)) {
            val op = Op.fromToken(advance())
            val right = parseUnary()
            left = Call(op, left, right)
        }
        return left
    }

    fun parseAdditive() : ASTNode {
        var left: ASTNode = parseMultiplicative()

        while(at(TokenType.PLUS) || at(TokenType.MINUS)) {
            val op = Op.fromToken(advance())
            val right = parseMultiplicative()
            left = Call(op, left, right)
        }
        return left
    }

    fun parse(): ASTNode {
        return parseAdditive()
    }

    companion object {
        fun parse(tokens: Tokens): ASTNode{
            return Parser(tokens).parse()
        }
    }
}