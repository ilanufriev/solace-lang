package solace.compiler.visitors

import solace.compiler.antlr.SolaceBaseVisitor
import solace.compiler.antlr.SolaceParser
import solace.vm.internal.harv.types.HarvFifo
import solace.vm.internal.harv.types.HarvInt
import solace.vm.internal.harv.types.HarvString
import solace.vm.internal.harv.asm.*

class SoftwareVisitor : SolaceBaseVisitor<Any>() {
    open class SoftwareVisitorException(val msg: String): IllegalArgumentException(msg)
    class InvalidTypesForOperation(val op: String, val type1: String, val type2: String) : SoftwareVisitorException("Invalid types for operation $op: $type1 and $type2")

    interface VisitResult

    data class ExprVisitResult(
        val instrs: List<Instruction> = listOf(),
        val type: String? = null
    ) : VisitResult

    class Node() {
        var name: String? = null
        var ins = listOf<String>()
        var outs = listOf<String>()
        var selves = listOf<String>()
        var initCode = mutableListOf<Instruction>()
        var runCode = mutableListOf<Instruction>()
        val declaredVariables = mutableMapOf<String, String>()
    }

    private var nodes= mutableListOf<Node>()
    private var ifCounter = 0

    private fun getInstructionsFromVisitResult(visitResult: VisitResult): List<Instruction> {
        when (visitResult) {
            is ExprVisitResult -> return visitResult.instrs
        }
        return listOf()
    }

    private fun getTypeFromVisitResult(visitResult: VisitResult): String? {
        when (visitResult) {
            is ExprVisitResult -> return visitResult.type
        }

        return null
    }

    override fun visitProgram(ctx: SolaceParser.ProgramContext?): List<Node> {
        ctx ?: return listOf()
        for (nodeDecl in ctx.nodeDecl()) {
            visit(nodeDecl)
        }

        val toReturn = nodes.toList()
        nodes.clear()

        return toReturn
    }

    override fun visitNodeDecl(ctx: SolaceParser.NodeDeclContext?) {
        ctx ?: return
        ctx.SOFTWARE() ?: return // if not hardware, we skip

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
        val initBlock = visitInitBlock(ctx.initBlock())
        val runBlock = visitRunBlock(ctx.runBlock())

        node.initCode.addAll(initBlock)
        node.initCode.addAll(runBlock.filter { i -> i.isInit })

        // all generated instructions are generated as non-init by default, so they need to marked in order
        // to work properly
        node.initCode.map { i -> i.isInit = true }

        // run code
        node.runCode.addAll(runBlock.filter { i -> !i.isInit })
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
                    Define(
                        HarvFifo.typeName,
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

    override fun visitInitBlock(ctx: SolaceParser.InitBlockContext?): List<Instruction> {
        ctx ?: return listOf()
        return (visit(ctx.block()) as List<*>).filterIsInstance<Instruction>()
    }

    override fun visitRunBlock(ctx: SolaceParser.RunBlockContext?): List<Instruction> {
        ctx ?: return listOf()
        return (visit(ctx.block()) as List<*>).filterIsInstance<Instruction>()
    }

    override fun visitBlock(ctx: SolaceParser.BlockContext?): List<Instruction> {
        val instructions = mutableListOf<Instruction>()
        ctx ?: return instructions
        for (child in ctx.children) {
            val value = visit(child) as VisitResult? ?: continue
            instructions.addAll(
                getInstructionsFromVisitResult(value)
            )
        }
        return instructions
    }

    override fun visitVarDeclStmt(ctx: SolaceParser.VarDeclStmtContext?): Any? {
        ctx ?: return null
        val instructions = mutableListOf<Instruction>()
        val varType = visit(ctx.type()) as String? ?: return null
        val varName = ctx.ID().text!!

        if (varType != HarvInt.typeName &&
            varType != HarvString.typeName
        ) {
            throw SoftwareVisitorException("Type $varType is unknown")
        }

        instructions.addLast(
            Define(
                varType,
                varName,
                true
            )
        )

        val currentNode = nodes.last()
        currentNode.declaredVariables[varName] = varType

        if (ctx.expr() == null) {
            return ExprVisitResult(instructions)
        }

        val value = visit(ctx.expr()) as VisitResult?
            ?: return ExprVisitResult(instructions)

        val type = getTypeFromVisitResult(value)
        if (type != varType) {
            SoftwareVisitorException("Invalid type assigned to variable $varName")
        }

        instructions.addAll(
            getInstructionsFromVisitResult(value)
        )

        instructions.addLast(
            Put(
                varName,
                true
            )
        )

        return ExprVisitResult(instructions)
    }

    override fun visitIfStmt(ctx: SolaceParser.IfStmtContext?): VisitResult? {
        ctx ?: return null

        val expr = visit(ctx.expr()) as VisitResult? ?: return null
        val blockIf = (visit(ctx.block(0)) as List<*>).filterIsInstance<Instruction>()
        var blockElse: List<Instruction>? = null
        if (ctx.ELSE() != null) {
            blockElse = (visit(ctx.block(1)) as List<*>).filterIsInstance<Instruction>()
        }

        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstructionsFromVisitResult(expr)
        )

        val labelIf = "labelIf$ifCounter"
        val labelElse = "labelElse$ifCounter"
        val labelEnd = "labelEnd$ifCounter"

        ifCounter++;

        instructions.addLast(
            Branch(
                labelIf,
                labelElse,
                labelEnd,
                false
            )
        )
        instructions.addLast(
            Label(labelIf)
        )

        instructions.addAll(
            blockIf
        )

        instructions.addLast(
            Goto(
                labelEnd
            )
        )

        instructions.addLast(
            Label(labelElse)
        )

        if (blockElse != null) {
            instructions.addAll(
                blockElse
            )
        }

        instructions.addLast(
            Goto(
                labelEnd
            )
        )

        instructions.addLast(
            Label(
                labelEnd
            )
        )

        return ExprVisitResult(instructions)
    }

    override fun visitExprStmt(ctx: SolaceParser.ExprStmtContext?): VisitResult? {
        ctx ?: return null
        return visit(ctx.expr()) as VisitResult?
    }

    override fun visitPrintStmt(ctx: SolaceParser.PrintStmtContext?): VisitResult? {
        ctx ?: return null
        val value = visit(ctx.expr()) as VisitResult? ?: return null
        val instructions = mutableListOf<Instruction>()
        instructions.addAll(
            getInstructionsFromVisitResult(value)
        )

        instructions.addLast(
            Print()
        )

        return ExprVisitResult(instructions)
    }

    override fun visitFifoWriteStmt(ctx: SolaceParser.FifoWriteStmtContext?): VisitResult? {
        ctx ?: return null

        val fifoName = ctx.ID().text!!
        val value = visit(ctx.expr()) as VisitResult? ?: return null
        val instructions = mutableListOf<Instruction>()

        val currentNode = nodes.last()
        if (!currentNode.outs.contains(fifoName) && !currentNode.selves.contains((fifoName))) {
            throw SoftwareVisitorException("Fifo $fifoName has not been declared as an out or self")
        }

        instructions.addAll(
            getInstructionsFromVisitResult(value)
        )

        instructions.addLast(
            Put(
                fifoName,
                false
            )
        )

        return ExprVisitResult(instructions)
    }

    override fun visitAssignStmt(ctx: SolaceParser.AssignStmtContext?): VisitResult? {
        ctx ?: return null

        val varName = ctx.ID().text!!
        val value = visit(ctx.expr()) as VisitResult? ?: return null
        val instructions = mutableListOf<Instruction>()

        val currentNode = nodes.last()
        if (!currentNode.declaredVariables.contains(varName)) {
            throw SoftwareVisitorException("Variable with name $varName has not been declared")
        }

        val type = getTypeFromVisitResult(value)
        if (type != currentNode.declaredVariables[varName]) {
            throw SoftwareVisitorException("Invalid type assigned to variable $varName")
        }

        instructions.addAll(
            getInstructionsFromVisitResult(value)
        )

        instructions.addLast(
            Put(
                varName,
                false
            )
        )

        return ExprVisitResult(instructions)
    }

    override fun visitType(ctx: SolaceParser.TypeContext?): String? {
        ctx ?: return null
        if (ctx.INT_TYPE() != null) {
            return HarvInt.typeName
        } else if (ctx.STRING_TYPE() != null) {
            return HarvString.typeName
        }

        return null
    }

    override fun visitPrimary(ctx: SolaceParser.PrimaryContext?): VisitResult? {
        ctx ?: return null
        if (ctx.INT_LITERAL() != null) {
            return ExprVisitResult(listOf(
                    Push(
                        ctx.INT_LITERAL().text,
                        null,
                        null,
                        false
                    ),
                ),
                HarvInt.typeName
            )
        } else if (ctx.STRING_LITERAL() != null) {
            val strValQuoted = ctx.STRING_LITERAL().text!!
            val strVal = strValQuoted.substring(1, strValQuoted.length - 1)
            return ExprVisitResult(listOf(
                    Push(
                        null,
                        strVal,
                        null,
                        false
                    )
                ),
                HarvString.typeName
            )
        } else if (ctx.ID() != null) {
            val id = ctx.ID().text!!
            val currentNode = nodes.last()
            if (!currentNode.declaredVariables.contains(id)) {
                throw SoftwareVisitorException("ID $id is referenced, but not declared")
            }

            return ExprVisitResult(listOf(
                    Push(
                        null,
                        null,
                        ctx.ID().text,
                        false
                    )
                ),
                currentNode.declaredVariables[id]!!
            )
        } else {
            return visit(ctx.expr()) as VisitResult?
        }
    }

    override fun visitNotExpr(ctx: SolaceParser.NotExprContext?): VisitResult? {
        ctx ?: return null
        val visitResult = visit(ctx.expr()) as VisitResult? ?: return null
        val instructions = mutableListOf<Instruction>()

        val type = getTypeFromVisitResult(visitResult)
        if (type == HarvString.typeName) {
            throw SoftwareVisitorException("Can't perform NOT on string")
        }

        instructions.addAll(
            getInstructionsFromVisitResult(visitResult)
        )

        instructions.addLast(
            Not()
        )



        return ExprVisitResult(instructions, type)
    }

    override fun visitNegExpr(ctx: SolaceParser.NegExprContext?): VisitResult? {
        ctx ?: return null
        val visitResult = visit(ctx.expr()) as VisitResult? ?: return null
        val instructions = mutableListOf<Instruction>()

        val type = getTypeFromVisitResult(visitResult)
        if (type == HarvString.typeName) {
            throw SoftwareVisitorException("Can't perform NOT on string")
        }

        instructions.addAll(
            getInstructionsFromVisitResult(visitResult)
        )

        instructions.addLast(
            Push(
                "-1",
                null,
                null,
                false
            )
        )

        instructions.addLast(
            Mul()
        )

        return ExprVisitResult(instructions, getTypeFromVisitResult(visitResult))
    }

    override fun visitFifoReadExpr(ctx: SolaceParser.FifoReadExprContext?): VisitResult? {
        ctx ?: return null
        val fifoName = ctx.ID().text!!
        if (ctx.QUESTION() != null) {
            return ExprVisitResult(listOf(
                    PushSize(
                        fifoName,
                        false
                    )
                ),
                HarvInt.typeName
            )
        }

        return ExprVisitResult(listOf(
                Push(
                    null,
                    null,
                    fifoName,
                    false
                )
            ),
            HarvInt.typeName
        )
    }

    private fun visitBinaryOp(arg1Result: VisitResult, arg2Result: VisitResult, op: Instruction, typeCheck: Boolean = true): VisitResult {
        val instructions = mutableListOf<Instruction>()
        if (typeCheck) {
            val type1 = getTypeFromVisitResult(arg1Result) ?: "null"
            val type2 = getTypeFromVisitResult(arg2Result) ?: "null"
            if (type1 != HarvInt.typeName ||
                type2 != HarvInt.typeName
            ) {
                throw InvalidTypesForOperation(op::class.simpleName!!.lowercase(), type1, type2)
            }
        }

        instructions.addAll(
            getInstructionsFromVisitResult(arg1Result)
        )

        instructions.addAll(
            getInstructionsFromVisitResult(arg2Result)
        )

        instructions.addLast(
            op
        )

        return ExprVisitResult(instructions, HarvInt.typeName)
    }

    override fun visitMulExpr(ctx: SolaceParser.MulExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Mul())
    }

    override fun visitDivExpr(ctx: SolaceParser.DivExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Div())
    }

    override fun visitAddExpr(ctx: SolaceParser.AddExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        val type1 = getTypeFromVisitResult(arg1)
        val type2 = getTypeFromVisitResult(arg2)
        val type = if (type1 != type2) HarvString.typeName else HarvInt.typeName

        val result = visitBinaryOp(arg1, arg2, Add(), false) as ExprVisitResult
        return ExprVisitResult(
            result.instrs,
            type
        )
    }

    override fun visitSubExpr(ctx: SolaceParser.SubExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Sub())
    }

    override fun visitShiftLeftExpr(ctx: SolaceParser.ShiftLeftExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = ExprVisitResult(listOf(
            Push(
                "2",
                null,
                null,
                false
            )
        ))
        return visitBinaryOp(arg1, arg2, Div())
    }

    override fun visitShiftRightExpr(ctx: SolaceParser.ShiftRightExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = ExprVisitResult(listOf(
            Push(
                "2",
                null,
                null,
                false
            )
        ))
        return visitBinaryOp(arg1, arg2, Mul())
    }

    override fun visitLtExpr(ctx: SolaceParser.LtExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Lt())
    }

    override fun visitLeExpr(ctx: SolaceParser.LeExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Le())
    }

    override fun visitGtExpr(ctx: SolaceParser.GtExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Gt())
    }

    override fun visitGeExpr(ctx: SolaceParser.GeExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Ge())
    }

    override fun visitEqExpr(ctx: SolaceParser.EqExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Eq())
    }

    override fun visitNeqExpr(ctx: SolaceParser.NeqExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Neq())
    }

    override fun visitAndExpr(ctx: SolaceParser.AndExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, And())
    }

    override fun visitOrExpr(ctx: SolaceParser.OrExprContext?): VisitResult? {
        ctx ?: return null
        val arg1 = visit(ctx.expr(0)) as VisitResult? ?: return null
        val arg2 = visit(ctx.expr(1)) as VisitResult? ?: return null

        return visitBinaryOp(arg1, arg2, Or())
    }
}