package symbolic

import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLParagraphElement
import kotlin.browser.document
import kotlin.js.Console
import kotlin.browser.window

fun runPasses(ast: ASTNode, vararg passes: Pass, debug: Boolean = false): ASTNode {
    return passes.fold(ast) { currAST, pass ->
        val newAST = pass.run(currAST)
        if (debug) println("${pass.name} =>\t$newAST")
        newAST
    }
}

fun normalize(input: String): ASTNode {
    val tokens = Lexer.tokenize(input)
    val ast = Parser.parse(tokens)
    println("=> $ast")
    return runPasses(
            ast,
            ExpandSub,
            ExpandConst,
            ExpandUnary,
            Distributive,
            CleanNegOnes,
            CleanZerosOnes,
            NegOnesToUnary,
            ReduceAddNegates,
            Normalize,
            debug = true
    )
}


fun main() {
    val button = document.getElementById("mybutton") as HTMLButtonElement
    button.addEventListener("click", {
        val input1 = document.getElementById("input1") as HTMLInputElement
        val input2 = document.getElementById("input2") as HTMLInputElement
        val output = try {
             if (normalize(input1.value) == normalize(input2.value)) "EQUAL!" else "UNEQUAL!"
        } catch (e: IllegalStateException) {
            "Error: " + e.message
        }
        val outputElem = (document.getElementById("output") as HTMLParagraphElement)
        outputElem.innerText = output
        outputElem.style.color = "#85D5F7"
        window.setTimeout(handler = {outputElem.style.color = "black"}, timeout = 200)
        outputElem.style.color = "#85D5F7"
    })
}