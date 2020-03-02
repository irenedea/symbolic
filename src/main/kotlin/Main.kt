package symbolic

fun runPasses(ast: ASTNode, vararg passes: Pass): ASTNode {
    var curr = ast
    passes.forEach{
        curr = it.run(curr)
        println("${it.name} =>\t$curr")
    }
    return curr
}

fun main() {
    while (true) {
        val input = readLine() ?: continue
        try {
            val tokens = Lexer.tokenize(input)
            val ast = Parser.parse(tokens)
            println("=> $ast")
            val output = runPasses(ast,CanonicalAdds, CanonicalMuls,Distributive,CanonicalAdds, CanonicalMuls)
            println("Tree is $output")
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }
}