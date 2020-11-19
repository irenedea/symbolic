package symbolic

import kotlin.math.min

interface Pass {
    val name: String
    fun run(ast: ASTNode): ASTNode
}

fun runUntilStable(ast: ASTNode, fn: (ASTNode) -> ASTNode): ASTNode {
    var prev = ast
    var curr = fn(ast)
    var count = 0
    while (prev.hashCode() != curr.hashCode()) {
        count += 1
        prev = curr
        curr = fn(prev)
    }
    return curr
}

fun recurseHelper(ast: ASTNode, fn: (ASTNode) -> ASTNode): ASTNode = when(ast) {
    is Call -> ast.copy(left = fn(ast.left), right = fn(ast.right))
    is FlatCall -> ast.copy(args = ast.args.map { fn(it) })
    is UnaryCall -> ast.copy(arg = fn(ast.arg))
    is Var, is Const -> ast
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
            Normalize.run(distributed)
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

object ExpandSub: Pass {
    override val name: String = "ExpandSub"
    private fun expandSub(ast: ASTNode): ASTNode =
        if (ast is Call && ast.op is Sub)
            Call(Add, expandSub(ast.left), UnaryCall(Negate, expandSub(ast.right)))
         else recurseHelper(ast, ::expandSub)

    override fun run(ast: ASTNode): ASTNode = expandSub(ast)
}

object ExpandConst: Pass {
    override val name: String = "ExpandConst"
    private fun addFromZero(value: Int): ASTNode {
        if (value == 0) {
            return Const(0)
        }
        return Call(Add, addFromZero(value - 1), Const(1))
    }

    private fun expand(ast: ASTNode): ASTNode = when(ast) {
        is Const -> addFromZero(ast.value)
        else -> recurseHelper(ast, ::expand)
    }
    override fun run(ast: ASTNode): ASTNode {
        return expand(ast)
    }
}

object ExpandUnary: Pass {
    override val name: String = "ExpandUnary"
    private fun expandUnary(ast: ASTNode): ASTNode {
        return when (ast) {
            is UnaryCall -> {
                if (ast.op is Negate) {
                    Call(Mul, Const(-1), expandUnary(ast.arg))
                } else {
                    UnaryCall(ast.op, expandUnary(ast.arg))
                }
            }
            is Call -> {
                Call(ast.op, expandUnary(ast.left), expandUnary(ast.right))
            }
            else -> recurseHelper(ast, ::expandUnary)
        }
    }
    override fun run(ast: ASTNode): ASTNode = expandUnary(ast)
}

object Normalize: Pass {
    override val name: String = "Normalize"
    override fun run(ast: ASTNode): ASTNode {
        return runPasses(ast, Flatten, CanonicalAdds, CanonicalMuls, Unflatten)
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
        else -> recurseHelper(ast, ::clean)
    }
    override fun run(ast: ASTNode): ASTNode = runUntilStable(ast) { clean(it) }
}

object CleanNegOnes: Pass {
    override val name: String = "CleanNegOnes"
    // Assume flattened, canonicalized muls.
    private fun clean(ast: ASTNode): ASTNode =
        if (ast is FlatCall && ast.op == Mul) {
            val args = ast.args
            val start = args.indexOfFirst { it.isConst(-1) }
            val end = args.indexOfLast { it.isConst(-1) }
            if (start != -1 && end != -1) {
                val noNegOnes = args.subList(0, start) + args.subList(end + 1, args.size)
                val numNegOnes = start - end + 1
                val newArgs = if (numNegOnes % 2 != 0) {
                    listOf(Const(-1), Const(1)) + noNegOnes
                } else {
                    listOf(Const(1), Const(1)) + noNegOnes // Add ones in case noNegOnes is completely empty.
                }
                ast.copy(args = newArgs.map { recurseHelper(it, ::clean) })
            } else
                recurseHelper(ast, ::clean)

        } else {
            recurseHelper(ast, ::clean)
        }

    override fun run(ast: ASTNode): ASTNode {
        val preprocess = CanonicalMuls.run(Flatten.run(ast))
        return Unflatten.run(clean(preprocess))
    }
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

object NegOnesToUnary: Pass {
    override val name: String = "NegOnesToUnary"

    private fun clean(ast: ASTNode): ASTNode =
        if (ast is FlatCall && ast.op is Mul && ast.args.first().isConst(-1)) {
            if (ast.args.size == 2) {
                UnaryCall(Negate, ast.args[1])
            } else
                UnaryCall(Negate, recurseHelper(FlatCall(ast.op, ast.args.subList(1, ast.args.size)), ::clean))
        } else recurseHelper(ast, ::clean)

    override fun run(ast: ASTNode): ASTNode {
        return runUntilStable(Flatten.run(ast), ::clean)
    }
}

object ReduceAddNegates: Pass {
    override val name: String = "ReduceAddNegates"

    private fun clean(ast: ASTNode): ASTNode {
        if (ast is FlatCall) {
            val newCall = ast.copy(args = ast.args.map { clean(it) })
            if (ast.op !is Add) {
                return newCall
            }
            val negatives = newCall.args.flatMap { if (it is UnaryCall && it.op is Negate) listOf(it.arg) else listOf() }
            val positives = newCall.args.filter { !(it is UnaryCall && it.op is Negate) }
            val groupNegatives = negatives.groupBy { it.hashCode() }.mapKeys { it.value.first() }.mapValues { it.value.size }
            val groupPositives = positives.groupBy { it.hashCode() }.mapKeys { it.value.first() }.mapValues { it.value.size }
            val newNegatives = groupNegatives.toMutableMap()
            val newPositives = groupPositives.toMutableMap()

            for (astKey in groupNegatives.keys intersect groupPositives.keys) {
                val numNegs = groupNegatives.getOrElse(astKey) { 0 }
                val numPos = groupPositives.getOrElse(astKey) { 0 }
                val smaller = min(numNegs, numPos)
                newNegatives[astKey] = numNegs - smaller
                newPositives[astKey] = numPos - smaller
            }
            val newArgs = mutableListOf<ASTNode>()
            newNegatives.forEach { for (i in 0 until it.value) newArgs.add(UnaryCall(Negate, it.key)) }
            newPositives.forEach { for (i in 0 until it.value) newArgs.add(it.key) }
            if (newArgs.size == 1) {
                return newArgs.first()
            } else if (newArgs.isEmpty()) {
                return Const(0)
            }
            return newCall.copy(args = newArgs)
        } else return recurseHelper(ast, ::clean)
    }

    override fun run(ast: ASTNode): ASTNode {
        val preprocessed = Flatten.run(ast)
        return clean(preprocessed)
    }
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