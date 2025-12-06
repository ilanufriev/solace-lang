package solace.compiler.visitors

import solace.compiler.antlr.SolaceBaseVisitor
import solace.compiler.antlr.SolaceParser
import solace.vm.internal.sim.asm.EncodedInstruction
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
import solace.vm.internal.sim.netlist.RBitShift
import solace.vm.internal.sim.netlist.Register

class HardwareVisitor : SolaceBaseVisitor<Any>() {
    enum class ExprResultType {
        IMMEDIATE,
        IDENTIFIER,
        LEAF
    }

    interface ExprResult

    data class LeafExprResult(
        val conLeafName: String,
        val conLeafPortName: String,
        val instrs: List<Instruction> = listOf()
    ) : ExprResult

    data class ImmediateExprResult(
        val text: String
    ) : ExprResult

    class Node() {
        var name: String? = null
        var ins = listOf<String>()
        var outs = listOf<String>()
        var selves = listOf<String>()
        var initCode = listOf<EncodedInstruction>()
        var runCode = listOf<EncodedInstruction>()
    }

    var nodes = mutableListOf<Node>()
    var instanceCounters = mutableMapOf<String, Int>()

    private fun generateNodeName(basename: String): String {
        if (!instanceCounters.contains(basename)) {
            instanceCounters[basename] = 0
        }

        val name = "$basename${instanceCounters[basename]!!}"
        instanceCounters[basename] = instanceCounters[basename]!! + 1
        return name
    }

    private fun getInstrsFromExprResult(exprResult: ExprResult): List<Instruction> {
        if (exprResult is LeafExprResult) {
            return exprResult.instrs
        } else if (exprResult is ImmediateExprResult) {
            // nop
        }

        return listOf()
    }

    private fun connectExprResultToLeaf(leafName: String,
                                        leafPortName: String,
                                        exprResult: ExprResult): List<Instruction> {
        val instrs = mutableListOf<Instruction>()

        if (exprResult is LeafExprResult) {
            instrs.addLast(
                Con(
                    exprResult.conLeafName,
                    exprResult.conLeafPortName,
                    leafName,
                    leafPortName,
                    false
                )
            )
        } else if (exprResult is ImmediateExprResult) {
            instrs.addLast(
                ImmCon(
                    leafName,
                    leafPortName,
                    exprResult.text,
                    false
                )
            )
        }

        return instrs
    }

    override fun visitNodeDecl(ctx: SolaceParser.NodeDeclContext?): Node? {
        ctx ?: return null
        // fifos = visit channel signature (if any)
        // init = visit init
        // run = visit run

        ctx.HARDWARE() ?: return null // if not hardware, we skip

        nodes.addLast(Node())
        val node = nodes.last()
        node.name = ctx.ID().text

        val channelSignature = visitChannelSignature(ctx.channelSignature());
        node.ins = channelSignature["in"]!!
        node.outs = channelSignature["out"]!!
        node.selves = channelSignature["self"]!!

        val initBlock = visitInitBlock(ctx.initBlock())
        val runBlock = visitRunBlock(ctx.runBlock())

        return node
    }

    override fun visitChannelSignature(ctx: SolaceParser.ChannelSignatureContext?): Map<String, List<String>> {
        val channels = mutableMapOf<String, MutableList<String>>(
            "in" to mutableListOf<String>(),
            "out" to mutableListOf<String>(),
            "self" to mutableListOf<String>(),
        )

        ctx ?: return channels

        for (clause in ctx.channelClause()) {
            val ids = visitIdList(clause.idList())
            if (clause.IN() != null) {
                channels["in"]!!.addAll(ids)
            } else if (clause.OUT() != null) {
                channels["out"]!!.addAll(ids)
            } else if (clause.SELF() != null) {
                channels["self"]!!.addAll(ids)
            }
        }

        return channels
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

    override fun visitHardwareInitBlock(ctx: SolaceParser.HardwareInitBlockContext?): Any? {
        ctx ?: return null
        return visitHardwareBlock(ctx.hardwareBlock())
    }

    override fun visitHardwareRunBlock(ctx: SolaceParser.HardwareRunBlockContext?): Any? {
        ctx ?: return null
        return visitHardwareBlock(ctx.hardwareBlock())
    }

    override fun visitHardwareBlock(ctx: SolaceParser.HardwareBlockContext?): Any? {
        ctx ?: return null
        for (stmt in ctx.hardwareStatement()) {
            visitHardwareStatement(stmt)
        }
    }

    override fun visitHardwareStatement(ctx: SolaceParser.HardwareStatementContext?): Any? {
        ctx ?: return null
        if (ctx.SEMI() != null) {
            // dot nothing
        } else if (ctx.hardwareVarDeclStmt() != null) {
            // visit declaration
        } else if (ctx.hardwareFifoWriteStmt() != null) {
            // visit hardware fifo write statement
        } else if (ctx.exprStmt() != null) {
            // visit expression
        } else if (ctx.printStmt() != null) {
            // visit print statement
        }
    }

    override fun visitHardwareVarDeclStmt(ctx: SolaceParser.HardwareVarDeclStmtContext?): Any? {
        ctx ?: return null
        val instructions = mutableListOf<Instruction>()
        if (ctx.expr() != null) {
            val registerID = ctx.ID().text
            instructions.addLast(New(Register::class.simpleName!!, registerID, false))
            if (ctx.expr() is SolaceParser.PrimaryExprContext) {
                val primaryCtx = (ctx.expr() as SolaceParser.PrimaryExprContext).primary()
                if (primaryCtx.ID() != null) {
                    val fromID = primaryCtx.ID().text
                    instructions.addLast(Con(fromID, "out", registerID, "in", false))
                } else if (primaryCtx.INT_LITERAL() != null) {
                    val imm = primaryCtx.INT_LITERAL().text
                    instructions.addLast(ImmCon(registerID, "in", imm, false))
                } else if (primaryCtx.STRING_LITERAL() != null) {
                    // nothing here for now
                } else if (primaryCtx.expr() != null) { // for parenthesis-enclosed exprs

                }
            }
        } else if (ctx.hardwareIfStmt() != null) {
            // gotta visit this guy
        }
    }

    override fun visitPrimaryExpr(ctx: SolaceParser.PrimaryExprContext?): ExprResult? {
        ctx ?: return null
        if (ctx.primary().INT_LITERAL() != null) {
            return ImmediateExprResult(ctx.primary().INT_LITERAL().text)
        } else if (ctx.primary().ID() != null) {
            return LeafExprResult(ctx.primary().ID().text, "out")
        } else if (ctx.primary().STRING_LITERAL() != null) {
            // for now this will do nothing
        }

        return visit(ctx.primary().expr()) as ExprResult?
    }

    private fun generateBinaryOperatorInstrs(leafName: String, leafTypeName: String,
                                             in1Expr: ExprResult, in2Expr: ExprResult): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromExprResult(
                in1Expr
            )
        )

        instructions.addAll(
            getInstrsFromExprResult(
                in2Expr
            )
        )

        instructions.addLast(
            New(
                leafName,
                leafTypeName,
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

    override fun visitSubExpr(ctx: SolaceParser.SubExprContext?): ExprResult? {
        ctx ?: return null
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        val multiplierTypeName = Multiplier::class.simpleName!!
        val multiplierLeafName = generateNodeName(multiplierTypeName)
        val multiplierInstructions = mutableListOf<Instruction>()

        // add a new expr result with an immediate value -1
        val minusOneExprResult = ImmediateExprResult("-1")

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
        val multiplierResult = LeafExprResult(
            multiplierLeafName,
            "out",
            multiplierInstructions
        )

        // create a new adder
        val adderLeafType = Adder::class.simpleName!!
        val adderLeafName = generateNodeName(adderLeafType)

        return LeafExprResult(
            adderLeafName,
            "out",
            generateBinaryOperatorInstrs(
                adderLeafName,
                adderLeafType,
                in1Expr,
                multiplierResult)
        )
    }

    override fun visitAddExpr(ctx: SolaceParser.AddExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = Adder::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)

        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitMulExpr(ctx: SolaceParser.MulExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = Multiplier::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitDivExpr(ctx: SolaceParser.DivExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = Divider::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitShiftLeftExpr(ctx: SolaceParser.ShiftLeftExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = LBitShift::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitShiftRightExpr(ctx: SolaceParser.ShiftRightExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = RBitShift::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitNegExpr(ctx: SolaceParser.NegExprContext?): ExprResult? {
        // -a = (a * (-1))
        ctx ?: return null

        val leafTypeName = Multiplier::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val inExpr = ctx.expr() as ExprResult
        val minusOneExpr = ImmediateExprResult("-1")

        return LeafExprResult(
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

    override fun visitNotExpr(ctx: SolaceParser.NotExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = LogicNot::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val inExpr = ctx.expr() as ExprResult

        val instructions = mutableListOf<Instruction>()

        // add all previous instructions into the list
        instructions.addAll(
            getInstrsFromExprResult(inExpr)
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

        return LeafExprResult(
            leafName,
            "out",
            instructions
        )
    }

    override fun visitLtExpr(ctx: SolaceParser.LtExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = CmpLess::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitLeExpr(ctx: SolaceParser.LeExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = CmpLeq::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
            leafName,
            "out",
            generateBinaryOperatorInstrs(
                leafName,
                leafTypeName,
                in1Expr,
                in2Expr)
        )
    }

    override fun visitGtExpr(ctx: SolaceParser.GtExprContext?): ExprResult? {
        // in1 > in2 = in2 < in1
        ctx ?: return null

        val leafTypeName = CmpLess::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
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

    override fun visitGeExpr(ctx: SolaceParser.GeExprContext?): ExprResult? {
        // in1 >= in2 = in2 <= in1
        ctx ?: return null

        val leafTypeName = CmpLeq::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
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

    override fun visitEqExpr(ctx: SolaceParser.EqExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = CmpEq::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
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

    override fun visitNeqExpr(ctx: SolaceParser.NeqExprContext?): ExprResult? {
        // a != b = !(a == b)
        ctx ?: return null
        val eqTypeName = CmpEq::class.simpleName!!
        val eqLeafName = generateNodeName(eqTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        val eqResult = LeafExprResult(
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
        val notLeafName = generateNodeName(notTypeName)

        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstrsFromExprResult(eqResult)
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

        return LeafExprResult(
            notLeafName,
            "out",
            instructions
        )
    }

    override fun visitAndExpr(ctx: SolaceParser.AndExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = LogicAnd::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
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

    override fun visitOrExpr(ctx: SolaceParser.OrExprContext?): ExprResult? {
        ctx ?: return null

        val leafTypeName = LogicOr::class.simpleName!!
        val leafName = generateNodeName(leafTypeName)
        val in1Expr = ctx.expr(0) as ExprResult
        val in2Expr = ctx.expr(1) as ExprResult

        return LeafExprResult(
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

    override fun visitFifoReadExpr(ctx: SolaceParser.FifoReadExprContext?): ExprResult? {
        ctx ?: return null

        val fifoType = Fifo::class.simpleName!!
        val fifoName = ctx.ID().text!!

        val instructions = mutableListOf<Instruction>()
        instructions.addLast(
            New(
                fifoType,
                fifoName,
                false
            )
        )

        return LeafExprResult(
            fifoName,
            generateNodeName("${fifoName}Out"),
            instructions
        )
    }
}