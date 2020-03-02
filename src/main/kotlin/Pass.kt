package symbolic

interface Pass {
    val name: String
    fun run(ast: ASTNode): ASTNode
}

object Distributive: Pass {
    override val name: String = "Distributive"
    private fun distributeRight(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Multiplicative) {
                ast.right.acceptDistributeLeft(ast.op, ast.left)
            } else {
                Call(ast.op, distributeRight(ast.left), distributeRight(ast.right))
            }
        }
        else -> ast
    }

    private fun distributeLeft(ast: ASTNode): ASTNode = when(ast) {
        is Call -> {
            if (ast.op is Multiplicative) {
                ast.left.acceptDistributeLeft(ast.op, ast.right)
            } else {
                Call(ast.op, distributeLeft(ast.left), distributeLeft(ast.right))
            }
        }
        else -> ast
    }
    override fun run(ast: ASTNode): ASTNode {
//        var prev = ast
//        var curr = distributeRight(ast)
//
//        while (curr.hashCode() == prev.hashCode()) {
//            prev = curr
//            curr = distributeRight(curr)
//        }
//        println("distributeRight $curr")
//        curr = distributeLeft(curr)
//        while (curr.hashCode() == prev.hashCode()) {
//            prev = curr
//            curr = distributeLeft(curr)
//        }
//        println("distributeLeft $curr")
//        return curr
        val a = distributeRight(ast)
//        println(a)
        val b = distributeLeft(a)
//        println(b)
//        println("-----")
//
//        val c = distributeLeft(ast)
//        println(a)
//        val d = distributeRight(a)
//        println(b)
        return b
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
        else -> ast
    }

    override fun run(ast: ASTNode): ASTNode {
        var prev = ast
        var curr = swap(ast)
        while (curr.hashCode() != prev.hashCode()) {
            prev = curr
            curr = swap(curr)
        }
        return swap(ast)
    }
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
        else -> ast
    }

    override fun run(ast: ASTNode): ASTNode {
        var prev = ast
        var curr = swap(ast)
        while (curr.hashCode() != prev.hashCode()) {
            prev = curr
            curr = swap(curr)
        }
        return swap(ast)
    }
}