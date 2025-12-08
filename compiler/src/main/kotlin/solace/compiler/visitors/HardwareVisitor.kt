package solace.compiler.visitors

import solace.compiler.antlr.SolaceBaseVisitor
import solace.compiler.antlr.SolaceParser
import solace.vm.internal.sim.asm.instructions.Con
import solace.vm.internal.sim.asm.instructions.ImmCon
import solace.vm.internal.sim.asm.instructions.Instruction
import solace.vm.internal.sim.asm.instructions.New
import solace.vm.internal.sim.netlist.Adder
import solace.vm.internal.sim.netlist.CmpEq
import solace.vm.internal.sim.netlist.CmpLeq
import solace.vm.internal.sim.netlist.CmpLess
import solace.vm.internal.sim.netlist.Divider
import solace.vm.internal.sim.netlist.Fifo
import solace.vm.internal.sim.netlist.LBitShift
import solace.vm.internal.sim.netlist.LogicAnd
import solace.vm.internal.sim.netlist.LogicNot
import solace.vm.internal.sim.netlist.LogicOr
import solace.vm.internal.sim.netlist.Multiplier
import solace.vm.internal.sim.netlist.Mux2
import solace.vm.internal.sim.netlist.RBitShift
import solace.vm.internal.sim.netlist.Register

class HardwareVisitor : SolaceBaseVisitor<Any>() {
    class HardwareVisitorException(val msg: String): IllegalArgumentException(msg)

    interface VisitResult

    data class LeafVisitResult(
        val conLeafName: String,
        val conLeafPortName: String,
        val instrs: List<Instruction> = listOf()
    ) : VisitResult

    data class ImmediateVisitResult(
        val text: String
    ) : VisitResult

    data class StmtVisitResult(
        val instrs: List<Instruction> = listOf()
    ) : VisitResult

    class Node() {
        var name: String? = null
        var ins = listOf<String>()
        var outs = listOf<String>()
        var selves = listOf<String>()
        var initCode = mutableListOf<Instruction>()
        var runCode = mutableListOf<Instruction>()
        val declaredRegisters = mutableListOf<String>()
    }

    private var nodes = mutableListOf<Node>()
    private var instanceCounters = mutableMapOf<String, Int>()

    private fun generateName(basename: String): String {
        if (!instanceCounters.contains(basename)) {
            instanceCounters[basename] = 0
        }

        val name = "$basename${instanceCounters[basename]!!}"
        instanceCounters[basename] = instanceCounters[basename]!! + 1
        return name
    }

    private fun getInstrsFromVisitResult(visitResult: VisitResult): List<Instruction> {
        when (visitResult) {
            is LeafVisitResult -> {
                return visitResult.instrs
            }

            is ImmediateVisitResult -> {
                // nop
            }

            is StmtVisitResult -> {
                return visitResult.instrs
            }
        }

        return listOf()
    }

    private fun connectExprResultToLeaf(leafName: String,
                                        leafPortName: String,
                                        visitResult: VisitResult): List<Instruction> {
        val instrs = mutableListOf<Instruction>()

        when (visitResult) {
            is LeafVisitResult -> instrs.addLast(
                Con(
                    visitResult.conLeafName,
                    visitResult.conLeafPortName,
                    leafName,
                    leafPortName,
                    false
                )
            )
            is ImmediateVisitResult -> instrs.addLast(
                ImmCon(
                    leafName,
                    leafPortName,
                    visitResult.text,
                    false
                )
            )
        }

        return instrs
    }

    override fun visitProgram(ctx: SolaceParser.ProgramContext?): List<Node> {
        ctx ?: return nodes
        for (nodeDecl in ctx.nodeDecl()) {
            visitNodeDecl(nodeDecl)
        }

        val toReturn = nodes.toList()
        nodes.clear()
        instanceCounters.clear()

        return toReturn
    }

    override fun visitNodeDecl(ctx: SolaceParser.NodeDeclContext?) {
        ctx ?: return
        ctx.HARDWARE() ?: return // if not hardware, we skip

        nodes.addLast(Node())
        val node = nodes.last()
        node.name = ctx.ID().text!!

        val channelSignatureResult = visitChannelSignature(ctx.channelSignature());
        node.ins = channelSignatureResult.first["in"]!!
        node.outs = channelSignatureResult.first["out"]!!
        node.selves = channelSignatureResult.first["self"]!!

        // fifo declarations
        node.initCode.addAll(channelSignatureResult.second.filter { i -> i.isInit })
        node.runCode.addAll(channelSignatureResult.second.filter { i -> !i.isInit })

        // init code
        node.initCode.addAll(visitHardwareInitBlock(ctx.hardwareInitBlock()))

        // all generated instructions are generated as non-init by default, so they need to marked in order
        // to work properly
        node.initCode.map { i -> i.isInit = true }

        // run code
        node.runCode.addAll(visitHardwareRunBlock(ctx.hardwareRunBlock()))
    }

    override fun visitChannelSignature(ctx: SolaceParser.ChannelSignatureContext?): Pair<Map<String, List<String>>, List<Instruction>> {
        val instructions = mutableListOf<Instruction>()
        val channels = mutableMapOf<String, MutableList<String>>(
            "in" to mutableListOf<String>(),
            "out" to mutableListOf<String>(),
            "self" to mutableListOf<String>(),
        )

        ctx ?: return Pair(channels, instructions)

        for (clause in ctx.channelClause()) {
            val ids = visitIdList(clause.idList())
            if (clause.IN() != null) {
                channels["in"]!!.addAll(ids)
            } else if (clause.OUT() != null) {
                channels["out"]!!.addAll(ids)
            } else if (clause.SELF() != null) {
                channels["self"]!!.addAll(ids)
            }

            for (id in ids) {
                // Same fifo must be declared in init and run
                instructions.addLast(
                    New(
                        Fifo::class.simpleName!!,
                        id,
                        false
                    )
                )
                instructions.addLast(
                    New(
                        Fifo::class.simpleName!!,
                        id,
                        true
                    )
                )
            }
        }

        return Pair(channels, instructions)
    }

    override fun visitIdList(ctx: SolaceParser.IdListContext?): List<String> {
        val ids = mutableListOf<String>()
        ctx ?: return ids
        ctx.ID() ?: return ids
        for (id in ctx.ID()) {
            ids.addLast(id.text)
        }

        return ids
    }

    override fun visitHardwareInitBlock(ctx: SolaceParser.HardwareInitBlockContext?): List<Instruction> {
        ctx ?: return listOf()
        return visitHardwareBlock(ctx.hardwareBlock())
    }

    override fun visitHardwareRunBlock(ctx: SolaceParser.HardwareRunBlockContext?): List<Instruction> {
        ctx ?: return listOf()
        return visitHardwareBlock(ctx.hardwareBlock())
    }

    override fun visitHardwareBlock(ctx: SolaceParser.HardwareBlockContext?): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        ctx ?: return instructions

        for (stmt in ctx.hardwareStatement()) {
            val visitResult = visitHardwareStatement(stmt) ?: continue
            instructions.addAll(
                getInstrsFromVisitResult(
                    visitResult
                )
            )
        }

        return instructions
    }

    override fun visitHardwareStatement(ctx: SolaceParser.HardwareStatementContext?): VisitResult? {
        ctx ?: return null

        var visitResult: VisitResult? = null
        if (ctx.SEMI() != null) {
            // do nothing
        } else if (ctx.hardwareVarDeclStmt() != null) {
            visitResult = visitHardwareVarDeclStmt(ctx.hardwareVarDeclStmt())
        } else if (ctx.hardwareFifoWriteStmt() != null) {
            visitResult = visitHardwareFifoWriteStmt(ctx.hardwareFifoWriteStmt())
        } else if (ctx.exprStmt() != null) {
            // basically a nop
        } else if (ctx.printStmt() != null) {
            // basically a nop (for now at least)
        }

        visitResult ?: return null

        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromVisitResult(visitResult)
        )

        return StmtVisitResult(
            instructions
        )
    }

    override fun visitHardwareFifoWriteStmt(ctx: SolaceParser.HardwareFifoWriteStmtContext?): VisitResult? {
        ctx ?: return null


        val fifoName = ctx.ID().text!!

        // Check if fifo is out or self
        val currentNode = nodes.last()
        if (!currentNode.outs.contains(fifoName) && !currentNode.selves.contains(fifoName)) {
            throw HardwareVisitorException("Fifo $fifoName is not out or self and cannot be written to")
        }

        var visitResult: VisitResult? = null

        if (ctx.expr() != null) {
            visitResult = visit(ctx.expr()) as VisitResult?
        } else if (ctx.hardwareIfStmt() != null) {
            visitResult = visitHardwareIfStmt(ctx.hardwareIfStmt())
        }

        visitResult ?: return null

        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromVisitResult(visitResult)
        )

        instructions.addAll(
            connectExprResultToLeaf(
                fifoName,
                generateName("in_$fifoName"),
                visitResult
            )
        )

        return StmtVisitResult(
            instructions
        )
    }

    override fun visitHardwareVarDeclStmt(ctx: SolaceParser.HardwareVarDeclStmtContext?): VisitResult? {
        ctx ?: return null
        val instructions = mutableListOf<Instruction>()
        var visitResult: VisitResult? = null

        if (ctx.expr() != null) {
            visitResult = visit(ctx.expr()) as VisitResult?
        } else if (ctx.hardwareIfStmt() != null) {
            visitResult = visit(ctx.hardwareIfStmt()) as VisitResult?
        }

        visitResult ?: return null

        val leafTypeName = Register::class.simpleName!!
        val leafName = ctx.ID().text!!

        val currentNode = nodes.last()
        if (currentNode.declaredRegisters.contains(leafName)) {
            throw HardwareVisitorException("Node ${currentNode.name} already has $leafName declared")
        }

        currentNode.declaredRegisters.addLast(leafName)

        instructions.addAll(
            getInstrsFromVisitResult(visitResult)
        )

        instructions.addLast(
            New(
                leafTypeName,
                leafName,
                false
            )
        )

        instructions.addAll(
            connectExprResultToLeaf(leafName, "in", visitResult)
        )

        return LeafVisitResult(
            leafName,
            "out",
            instructions
        )
    }

    override fun visitHardwareIfStmt(ctx: SolaceParser.HardwareIfStmtContext?): VisitResult? {
        ctx ?: return null
        val leafTypeName = Mux2::class.simpleName!!
        val leafName = generateName(leafTypeName)

        val selResult = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in1Result = visit(ctx.expr(1)) as VisitResult? ?: return null
        val in0Result = visit(ctx.expr(2)) as VisitResult? ?: return null

        val instructions = mutableListOf<Instruction>()
        
        instructions.addAll(
            getInstrsFromVisitResult(selResult)
        )

        instructions.addAll(
            getInstrsFromVisitResult(in0Result)
        )

        instructions.addAll(
            getInstrsFromVisitResult(in1Result)
        )

        instructions.addLast(
            New(
                leafTypeName,
                leafName,
                false,
            )
        )

        instructions.addAll(
            connectExprResultToLeaf(leafName, "sel", selResult)
        )

        instructions.addAll(
            connectExprResultToLeaf(leafName, "in0", in0Result)
        )

        instructions.addAll(
            connectExprResultToLeaf(leafName, "in1", in1Result)
        )

        return LeafVisitResult(
            leafName,
            "out",
            instructions
        )
    }

    override fun visitPrimaryExpr(ctx: SolaceParser.PrimaryExprContext?): VisitResult? {
        ctx ?: return null
        if (ctx.primary().INT_LITERAL() != null) {
            return ImmediateVisitResult(ctx.primary().INT_LITERAL().text)
        } else if (ctx.primary().ID() != null) {
            return LeafVisitResult(ctx.primary().ID().text, "out")
        } else if (ctx.primary().STRING_LITERAL() != null) {
            // for now this will do nothing
        }

        return visit(ctx.primary().expr()) as VisitResult?
    }

    private fun generateBinaryOperatorInstrs(leafName: String, leafTypeName: String,
                                             in1Expr: VisitResult, in2Expr: VisitResult): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromVisitResult(
                in1Expr
            )
        )

        instructions.addAll(
            getInstrsFromVisitResult(
                in2Expr
            )
        )

        instructions.addLast(
            New(
                leafTypeName,
                leafName,
                false
            )
        )

        instructions.addAll(
            connectExprResultToLeaf(
                leafName,
                "in1",
                in1Expr
            )
        )

        instructions.addAll(
            connectExprResultToLeaf(
                leafName,
                "in2",
                in2Expr
            )
        )

        return instructions
    }

    override fun visitSubExpr(ctx: SolaceParser.SubExprContext?): VisitResult? {
        ctx ?: return null
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        val multiplierTypeName = Multiplier::class.simpleName!!
        val multiplierLeafName = generateName(multiplierTypeName)
        val multiplierInstructions = mutableListOf<Instruction>()

        // add a new expr result with an immediate value -1
        val minusOneExprResult = ImmediateVisitResult("-1")

        // This will generate a list of instructions that will
        // create a mutliplier and connect in2Expr and -1 to it
        multiplierInstructions.addAll(
            generateBinaryOperatorInstrs(
                multiplierLeafName,
                multiplierTypeName,
                minusOneExprResult,
                in2Expr
            )
        )

        // add a new multiplier expr result to connect to the adder
        val multiplierResult = LeafVisitResult(
            multiplierLeafName,
            "out",
            multiplierInstructions
        )

        // create a new adder
        val adderLeafType = Adder::class.simpleName!!
        val adderLeafName = generateName(adderLeafType)

        return LeafVisitResult(
            adderLeafName,
            "out",
            generateBinaryOperatorInstrs(
                adderLeafName,
                adderLeafType,
                in1Expr,
                multiplierResult)
        )
    }

    override fun visitAddExpr(ctx: SolaceParser.AddExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = Adder::class.simpleName!!
        val leafName = generateName(leafTypeName)

        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitMulExpr(ctx: SolaceParser.MulExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = Multiplier::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitDivExpr(ctx: SolaceParser.DivExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = Divider::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitShiftLeftExpr(ctx: SolaceParser.ShiftLeftExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = LBitShift::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitShiftRightExpr(ctx: SolaceParser.ShiftRightExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = RBitShift::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitNegExpr(ctx: SolaceParser.NegExprContext?): VisitResult? {
        // -a = (a * (-1))
        ctx ?: return null

        val leafTypeName = Multiplier::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val inExpr = ctx.expr() as VisitResult? ?: return null
        val minusOneExpr = ImmediateVisitResult("-1")

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                inExpr,
                minusOneExpr
            )
        )
    }

    override fun visitNotExpr(ctx: SolaceParser.NotExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = LogicNot::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val inExpr = ctx.expr() as VisitResult? ?: return null

        val instructions = mutableListOf<Instruction>()

        // add all previous instructions into the list
        instructions.addAll(
            getInstrsFromVisitResult(inExpr)
        )

        // create our new leaf
        instructions.addLast(
            New(
                leafTypeName,
                leafName,
                false
            )
        )

        // connect our new leaf to previous instructions
        instructions.addAll(
            connectExprResultToLeaf(
                leafName,
                "in1",
                inExpr
            )
        )

        return LeafVisitResult(
            leafName,
            "out",
            instructions
        )
    }

    override fun visitLtExpr(ctx: SolaceParser.LtExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = CmpLess::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitLeExpr(ctx: SolaceParser.LeExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = CmpLeq::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitGtExpr(ctx: SolaceParser.GtExprContext?): VisitResult? {
        // in1 > in2 = in2 < in1
        ctx ?: return null

        val leafTypeName = CmpLess::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in2Expr,
                in1Expr
            )
        )
    }

    override fun visitGeExpr(ctx: SolaceParser.GeExprContext?): VisitResult? {
        // in1 >= in2 = in2 <= in1
        ctx ?: return null

        val leafTypeName = CmpLeq::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in2Expr,
                in1Expr
            )
        )
    }

    override fun visitEqExpr(ctx: SolaceParser.EqExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = CmpEq::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr
            )
        )
    }

    override fun visitNeqExpr(ctx: SolaceParser.NeqExprContext?): VisitResult? {
        // a != b = !(a == b)
        ctx ?: return null
        val eqTypeName = CmpEq::class.simpleName!!
        val eqLeafName = generateName(eqTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        val eqResult = LeafVisitResult(
            eqLeafName,
            "out",
            generateBinaryOperatorInstrs(
                eqLeafName,
                eqTypeName,
                in1Expr,
                in2Expr
            )
        )

        val notTypeName = LogicNot::class.simpleName!!
        val notLeafName = generateName(notTypeName)

        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromVisitResult(eqResult)
        )

        instructions.addLast(
            New(
                notTypeName,
                notLeafName,
                false
            )
        )

        instructions.addAll(
            connectExprResultToLeaf(
                notLeafName,
                "in",
                eqResult
            )
        )

        return LeafVisitResult(
            notLeafName,
            "out",
            instructions
        )
    }

    override fun visitAndExpr(ctx: SolaceParser.AndExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = LogicAnd::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr
            )
        )
    }

    override fun visitOrExpr(ctx: SolaceParser.OrExprContext?): VisitResult? {
        ctx ?: return null

        val leafTypeName = LogicOr::class.simpleName!!
        val leafName = generateName(leafTypeName)
        val in1Expr = visit(ctx.expr(0)) as VisitResult? ?: return null
        val in2Expr = visit(ctx.expr(1)) as VisitResult? ?: return null

        return LeafVisitResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr
            )
        )
    }

    override fun visitFifoReadExpr(ctx: SolaceParser.FifoReadExprContext?): VisitResult? {
        ctx ?: return null

        val fifoType = Fifo::class.simpleName!!
        val fifoName = ctx.ID().text!!
        val currentNode = nodes.last()

        if (!currentNode.ins.contains(fifoName) && !currentNode.selves.contains(fifoName)) {
            throw HardwareVisitorException("Fifo $fifoName is not in or self to be read from")
        }

        val instructions = mutableListOf<Instruction>()

        if (ctx.QUESTION() == null) {
            return LeafVisitResult(
                fifoName,
                generateName("out_$fifoName"),
                instructions
            )
        }

        return LeafVisitResult(
            fifoName,
            "size",
            instructions
        )
    }
}