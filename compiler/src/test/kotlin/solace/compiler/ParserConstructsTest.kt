package solace.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser

class ParserConstructsTest {
    private fun parse(source: String): Pair<SolaceParser, SolaceParser.ProgramContext> {
        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        return parser to tree
    }

    @Test
    fun parsesExpressionsAndControlFlow() {
        val (parser, tree) = parse(
            """
            node Calc : software () {
                init { int a = 1; string s = "hi"; }
                run {
                    int x = 1 + 2 * 3 << 1;
                    int y = -x + 5;
                    if (x > 0 && y != 3 || x == y) { int z = x; } else { print("no"); }
                }
            }
            """.trimIndent()
        )

        val pretty = prettyTree(tree, parser)
        assertTrue(hasNode<SolaceParser.IfStmtContext>(tree), "Should parse if/else")
        assertTrue(hasNode<SolaceParser.ShiftLeftExprContext>(tree), "Should parse shift-left expression")
        assertTrue(hasNode<SolaceParser.OrExprContext>(tree), "Should parse logical OR expression")
    }

    @Test
    fun parsesFifoReadWriteAndSelfOptional() {
        val (parser, tree) = parse(
            """
            node IO : hardware (
                in: inp;
                out: outp;
                self: selfp;
            ) {
                init { }
                run {
                    v = ${'$'}inp;
                    outp <- v;
                    opt = ${'$'}selfp?;
                    selfp <- opt;
                }
            }
            """.trimIndent()
        )

        val pretty = prettyTree(tree, parser)
        assertTrue(hasNode<SolaceParser.FifoWriteStmtContext>(tree), "Should parse fifo write statement")
        assertTrue(hasNode<SolaceParser.FifoReadExprContext>(tree), "Should parse fifo read expression")
    }

    @Test
    fun parsesNetworkDeclaration() {
        val (parser, tree) = parse(
            """
            node A : hardware ( out: o; ) { init { } run { } }
            node B : software ( in: i; ) { init { } run { } }
            network {
                A.o -> B.i;
            }
            """.trimIndent()
        )

        val pretty = prettyTree(tree, parser)
        assertTrue(hasNode<SolaceParser.NetworkDeclContext>(tree), "Should parse network declaration")
        assertTrue(hasNode<SolaceParser.ConnectionContext>(tree), "Should parse connection entries")
    }

    @Test
    fun parsesMinimalNode() {
        val (parser, tree) = parse(
            """
            node A : hardware () {
                init { }
                run  { }
            }
            """.trimIndent()
        )

        val pretty = prettyTree(tree, parser)
        assertTrue(hasNode<SolaceParser.NodeDeclContext>(tree), "Pretty tree should include nodeDecl")
    }

    private fun hasNode(tree: ParseTree, matcher: (ParseTree) -> Boolean): Boolean {
        if (matcher(tree)) return true
        for (i in 0 until tree.childCount) {
            if (hasNode(tree.getChild(i), matcher)) return true
        }
        return false
    }

    private inline fun <reified T> hasNode(tree: ParseTree): Boolean =
        hasNode(tree) { it is T }
}
