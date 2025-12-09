package solace.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser
import solace.compiler.visitors.SoftwareVisitor
import solace.vm.internal.harv.asm.AsmParser
import solace.vm.internal.harv.asm.EncodedInstruction
import solace.vm.StackMachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftwareVisitorTest {
    private fun parse(source: String): Pair<SolaceParser, SolaceParser.ProgramContext> {
        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        return parser to tree
    }

    @Test fun testAdderNode() {
        val visitor = SoftwareVisitor()
        val (parser, tree) = parse($$"""
            node Adder : software (
                in: in1, in2;
                out: outp;
            ) {
                init {
                }
            
                run {
                    outp <- $in1 + $in2;
                }
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<SoftwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        assertEquals("Adder", node.name)
        assertTrue(node.ins.contains("in1"))
        assertTrue(node.outs.contains("outp"))

//        println(node.initCode.joinToString("\n"))
//        println(node.runCode.joinToString("\n"))
    }

    @Test fun testBasicNode() {
        val visitor = SoftwareVisitor()
        val (parser, tree) = parse($$"""
            node Counter : software (
                out: numbers;
                self: loop;
            ) {
                init {
                    int x;
                    loop <- 0;
                }
            
                run {
                    x = $loop + 1;
            
                    // отправляем текущее значение в выходной FIFO
                    numbers <- x;
                    loop <- x;
                }
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<SoftwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        assertEquals("Counter", node.name)
        assertTrue(node.outs.contains("numbers"))
        assertTrue(node.selves.contains("loop"))
        assertTrue(node.declaredVariables.contains("x"))

        println(node.initCode.joinToString("\n"))
        println(node.runCode.joinToString("\n"))

        val byteCodeInstrs = mutableListOf<EncodedInstruction>()
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.initCode))
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.runCode))
        val byteCode = byteCodeInstrs.joinToString("")

        var harv = StackMachine()
        harv.loadByteCode(byteCode)
        var result = harv.tryInit()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(1, harv.getFifoSize("loop"))
        assertEquals(0, harv.pullFromFifo("loop"))

        harv = StackMachine()
        harv.loadByteCode(byteCode)
        result = harv.tryInit()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(1, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(2, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(3, harv.getFifoSize("numbers"))
        result = harv.tryRun()
        assertEquals(StackMachine.ExecStatus.SUCCESS, result)
        assertEquals(4, harv.getFifoSize("numbers"))

        assertEquals(1, harv.pullFromFifo("numbers"))
        assertEquals(2, harv.pullFromFifo("numbers"))
        assertEquals(3, harv.pullFromFifo("numbers"))
        assertEquals(4, harv.pullFromFifo("numbers"))
    }

    @Test fun testIfElse() {
        val visitor = SoftwareVisitor()
        val (parser, tree) = parse($$"""
            node AntiCounter : software (
                out: numbers;
                self: loop;
            ) {
                init {
                    int x = 0;
                    loop <- 10;
                }
            
                run {
                    x = $loop - 1;
            
                    // отправляем текущее значение в выходной FIFO
                    numbers <- x;
                    if (x == 0) {
                        loop <- 10;
                        string y = "hello world!";
                    }
                    
                    string y2;
                    
                    if (1) {
                        y2 = "yo";
                    } else {
                        y2 = "hello";
                    }
                    
                    loop <- x;
                }
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<SoftwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        assertEquals("AntiCounter", node.name)
        assertTrue(node.outs.contains("numbers"))
        assertTrue(node.selves.contains("loop"))
        assertTrue(node.declaredVariables.contains("x"))

//        println(node.initCode.joinToString("\n"))
//        println(node.runCode.joinToString("\n"))

        val byteCodeInstrs = mutableListOf<EncodedInstruction>()
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.initCode))
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.runCode))
    }

    @Test fun justCompile() {
        val visitor = SoftwareVisitor()
        val (parser, tree) = parse($$"""node AntiPrinter : software (
                in: numbers;
            ) {
                init {
                    int x = 0;
                    print("Start!");
                }
            
                run {
                    x = $numbers;
                    print(x);
                    // print("Size: " + $numbers?);
                }
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<SoftwareVisitor.Node>()
        val node = nodeList.first()
        println(node.initCode.joinToString("\n"))
        println(node.runCode.joinToString("\n"))
    }
}