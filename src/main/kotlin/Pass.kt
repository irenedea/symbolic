package symbolic

interface Pass {
    val name: String
    fun run(ast: ASTNode): ASTNode
}

fun runUntilStable(ast: ASTNode, fn: (ASTNode) -> ASTNode): ASTNode {
    var prev = ast
    var curr = fn(ast)
    while (prev.hashCode() != curr.hashCode()) {
        prev = curr
        curr = fn(prev)
    }
    return curr
}

object Distributive: Pass {
    override val name: String = "Distributive"
    private fun distributeRight(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Multiplicative) {
                ast.left.acceptDistributeRight(ast.op, ast.right)
            } else {
                Call(ast.op, distributeRight(ast.left), distributeRight(ast.right))
            }
        }
        else -> ast
    }

    private fun distributeLeft(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Multiplicative) {
                ast.right.acceptDistributeLeft(ast.op, ast.left)
            } else {
                Call(ast.op, distributeLeft(ast.left), distributeLeft(ast.right))
            }
        }
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode =
        runUntilStable(ast) { input ->
            val distributed = distributeLeft(distributeRight(input))
            runPasses(distributed, Flatten, CanonicalAdds, CanonicalMuls, Unflatten)
        }
}

object Flatten: Pass {
    override val name: String = "Flatten"
    fun flattenCallArgs(ast: ASTNode, op: Op): List<ASTNode> = when (ast) {
        is Call -> {
            if (op == ast.op)
                flattenCallArgs(ast.left, op) + flattenCallArgs(ast.right, op)
            else
                listOf(flatten(ast))

        }
        else -> listOf(ast)
    }
    private fun flatten(ast: ASTNode): ASTNode = when (ast) {
        is Call -> {
            FlatCall(ast.op, flattenCallArgs(ast, ast.op))
        }
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { flatten(it) }
}

object ExpandConst: Pass {
    override val name: String = "Expand"
    private fun addFromZero(value: Int): ASTNode {
        if (value == 0) {
            return Const(0)
        }
        return Call(Add, addFromZero(value - 1), Const(1))
    }

    private fun expand(ast: ASTNode): ASTNode = when(ast) {
        is Const -> addFromZero(ast.value)
        is Call -> Call(ast.op, expand(ast.left), expand(ast.right))
        is FlatCall -> FlatCall(ast.op, ast.args.map { expand(it) })
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode {
        return expand(ast)
    }
}

fun ASTNode.isConst(value: Int): Boolean {
    return this is Const && this.value == value
}

object CleanZerosOnes: Pass {
    override val name: String = "CleanZerosOnes"
    private fun clean(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op == Mul) {
                if (ast.left.isConst(0) || ast.right.isConst(0)) {
                    Const(0)
                } else if (ast.left.isConst(1)) {
                    clean(ast.right)
                } else if (ast.right.isConst(1)) {
                    clean(ast.left)
                } else {
                    Call(ast.op, clean(ast.left), clean(ast.right))
                }
            }
            else if (ast.op == Add || ast.op == Sub) {
                if (ast.left.isConst(0)) {
                    clean(ast.right)
                } else if (ast.right.isConst(0)) {
                    clean(ast.left)
                } else
                    Call(ast.op, clean(ast.left), clean(ast.right))
            }
            else {
                Call(ast.op, clean(ast.left), clean(ast.right))
            }
        }
        is FlatCall -> throw NotImplementedError()
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { clean(it) }
}

object Unflatten: Pass {
    override val name: String = "Unflatten"
    fun unflattenCallArgs(argList: List<ASTNode>, op: Op): ASTNode {
        if (argList.size == 1) {
            return argList.first()
        }
        return Call(op, unflattenCallArgs(argList.subList(0,argList.size - 1), op), argList.last())
    }
    private fun unflatten(ast: ASTNode): ASTNode = when (ast) {
        is Call -> {
            Call(ast.op, unflatten(ast.left), unflatten(ast.right))
        }
        is FlatCall -> {
            unflattenCallArgs(ast.args, ast.op)
        }
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { unflatten(it) }
}

object CanonicalAdds: Pass {
    override val name: String = "CanonicalAdds"
    private fun swap(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Add) {
                val newCall = Call(ast.op, swap(ast.left), swap(ast.right))
                if (newCall.left.hashCode() > newCall.right.hashCode()) {
                    Call(ast.op, newCall.right, newCall.left)
                } else newCall
            } else {
                Call(ast.op, swap(ast.left), swap(ast.right))
            }
        }
        is FlatCall -> {
            val newCall = FlatCall(ast.op, ast.args.map { swap(it) })
            if (ast.op is Add) {
                FlatCall(newCall.op, newCall.args.sortedBy { it.hashCode() })
            } else {
                newCall
            }
        }
        else -> ast
    }

    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { swap(it) }
}

object CanonicalMuls: Pass {
    override val name: String = "CanonicalMuls"
    private fun swap(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Mul) {
                val newCall = Call(ast.op, swap(ast.left), swap(ast.right))
                if (newCall.left.hashCode() > newCall.right.hashCode()) {
                    Call(ast.op, newCall.right, newCall.left)
                } else newCall
            } else {
                Call(ast.op, swap(ast.left), swap(ast.right))
            }
        }
        is FlatCall -> {
            val newCall = FlatCall(ast.op, ast.args.map { swap(it) })
            if (ast.op is Mul) {
                FlatCall(newCall.op, newCall.args.sortedBy { it.hashCode() })
            } else {
                newCall
            }
        }
        else -> ast
    }

    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { swap(it) }
}