package solace.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser

class ParserSmokeTest {
    @Test
    fun parsesMinimalNode() {
        val source = """
            node A : hardware () {
                init { }
                run  { }
            }
        """.trimIndent()

        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()

        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        val pretty = prettyTree(tree, parser)
        assertTrue(pretty.contains("nodeDecl"), "Pretty tree should include nodeDecl")
    }
}
