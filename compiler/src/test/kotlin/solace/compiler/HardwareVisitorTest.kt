package solace.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import solace.compiler.antlr.SolaceLexer
import solace.compiler.antlr.SolaceParser
import solace.compiler.visitors.HardwareVisitor
import solace.vm.Simulator
import solace.vm.internal.sim.asm.AsmParser
import solace.vm.internal.sim.asm.EncodedInstruction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardwareVisitorTest {
    private fun parse(source: String): Pair<SolaceParser, SolaceParser.ProgramContext> {
        val parser = SolaceParser(CommonTokenStream(SolaceLexer(CharStreams.fromString(source))))
        val tree = parser.program()
        assertEquals(0, parser.numberOfSyntaxErrors, "Parser should not report syntax errors")
        return parser to tree
    }

    @Test
    fun hardwareVisitorBasicTest() {
        val visitor = HardwareVisitor()
        val (parser, tree) = parse($$"""
            node Counter : hardware (
                out: numbers;
                self: loop;
            ) {
                init {
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

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<HardwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        assertEquals("Counter", node.name)
        assertTrue(node.outs.contains("numbers"))
        assertTrue(node.selves.contains("loop"))
        assertTrue(node.declaredRegisters.contains("x"))

        val byteCodeInstrs = mutableListOf<EncodedInstruction>()
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.initCode))
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.runCode))

        val byteCode = byteCodeInstrs.joinToString("")

        var sim = Simulator()
        sim.loadByteCode(byteCode)
        var result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("loop"))
        assertEquals(0, sim.pullFromFifo("loop"))

        sim = Simulator()
        sim.loadByteCode(byteCode)
        result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(2, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(3, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(4, sim.getFifoSize("numbers"))

        assertEquals(1, sim.pullFromFifo("numbers"))
        assertEquals(2, sim.pullFromFifo("numbers"))
        assertEquals(3, sim.pullFromFifo("numbers"))
        assertEquals(4, sim.pullFromFifo("numbers"))
    }

    @Test
    fun hardwareVisitorNegativeNumbersTest() {
        val visitor = HardwareVisitor()
        val (parser, tree) = parse($$"""
            node AntiCounter : hardware (
                out: numbers;
                self: loop;
            ) {
                init {
                    loop <- 10;
                }
            
                run {
                    x = $loop - 1;
            
                    // отправляем текущее значение в выходной FIFO
                    numbers <- x;
                    loop <- if (x == 0) 10 else x;
                }
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<HardwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        assertEquals("AntiCounter", node.name)
        assertTrue(node.outs.contains("numbers"))
        assertTrue(node.selves.contains("loop"))
        assertTrue(node.declaredRegisters.contains("x"))

        val byteCodeInstrs = mutableListOf<EncodedInstruction>()
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.initCode))
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.runCode))

        val byteCode = byteCodeInstrs.joinToString("")

        var sim = Simulator()
        sim.loadByteCode(byteCode)
        var result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("loop"))
        assertEquals(10, sim.pullFromFifo("loop"))
        assertEquals(0, sim.getFifoSize("numbers"))

        sim = Simulator()
        sim.loadByteCode(byteCode)
        result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(1, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(2, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(3, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(4, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(5, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(6, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(7, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(8, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(9, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(10, sim.getFifoSize("numbers"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)
        assertEquals(11, sim.getFifoSize("numbers"))

        assertEquals(9, sim.pullFromFifo("numbers"))
        assertEquals(8, sim.pullFromFifo("numbers"))
        assertEquals(7, sim.pullFromFifo("numbers"))
        assertEquals(6, sim.pullFromFifo("numbers"))
        assertEquals(5, sim.pullFromFifo("numbers"))
        assertEquals(4, sim.pullFromFifo("numbers"))
        assertEquals(3, sim.pullFromFifo("numbers"))
        assertEquals(2, sim.pullFromFifo("numbers"))
        assertEquals(1, sim.pullFromFifo("numbers"))
        assertEquals(0, sim.pullFromFifo("numbers"))
        assertEquals(9, sim.pullFromFifo("numbers"))
    }

    @Test
    fun hardwareVisitorTestAdder() {
        val visitor = HardwareVisitor()
        val (parser, tree) = parse($$"""
            node Adder : hardware (
                in: inp;
                out: outp;
            ) {
                init {}
                run {
                    outp <- $inp + $inp;
                }
            }
        """.trimMargin())
        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<HardwareVisitor.Node>()
        assertEquals(1, nodeList.size)

        val node = nodeList.first()

        val byteCodeInstrs = mutableListOf<EncodedInstruction>()
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.initCode))
        byteCodeInstrs.addAll(AsmParser.encodeInstructions(node.runCode))

        val byteCode = byteCodeInstrs.joinToString("")
        var sim = Simulator()
        sim.loadByteCode(byteCode)

        var result = sim.tryInit()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)

        sim.pushToFifo("inp", 2)
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.BLOCKED, result)

        sim.pushToFifo("inp", 4)
        assertEquals(2, sim.getFifoSize("inp"))
        result = sim.tryRun()
        assertEquals(Simulator.ExecStatus.SUCCESS, result)

        assertEquals(1, sim.getFifoSize("outp"))
        assertEquals(6, sim.pullFromFifo("outp"))
    }

    @Test
    fun hardwareVisitorMultipleNodesTest() {
        val visitor = HardwareVisitor()
        val (parser, tree) = parse($$"""
            node AntiCounter : hardware (
                out: numbers;
                self: loop;
            ) {
                init {
                    loop <- 10;
                }
            
                run {
                    x = $loop - 1;
            
                    // отправляем текущее значение в выходной FIFO
                    numbers <- x;
                    loop <- if (x == 0) 10 else x;
                }
            }
            node Adder : hardware (
                in: inp;
                out: outp;
            ) {
                init {}
                run {
                    outp <- $inp + $inp;
                }
            }
            node SoftwareNode : software (
                in: inp1, inp2;
                out: outp;
            ) {
                init {}
                run {}
            }
        """.trimMargin())

        val nodeList = (visitor.visit(tree) as List<*>).filterIsInstance<HardwareVisitor.Node>()
        assertEquals(2, nodeList.size)
        assertEquals("AntiCounter", nodeList[0].name)
        assertEquals("Adder", nodeList[1].name)
    }
}