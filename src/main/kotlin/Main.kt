package symbolic

fun runPasses(ast: ASTNode, vararg passes: Pass, debug: Boolean = false): ASTNode {
    return passes.fold(ast) { currAST, pass ->
        val newAST = pass.run(currAST)
        if (debug) println("${pass.name} =>\t$newAST")
        newAST
    }
}

fun main() {
    while (true) {
        val input = readLine() ?: continue
        try {
            val tokens = Lexer.tokenize(input)
            val ast = Parser.parse(tokens)
            println("=> $ast")
            val output = runPasses(
                    ast,
                    ExpandSub,
                    ExpandConst,
                    ExpandUnary,
                    Distributive,
                    CleanNegOnes,
                    CleanZerosOnes,
                    Normalize,
                    debug = true
            )
            println("Tree is $output")
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}