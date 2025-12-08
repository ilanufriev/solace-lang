package solace.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import solace.compiler.antlr.SolaceBaseVisitor
import solace.compiler.antlr.SolaceParser

enum class NodeType { HARDWARE, SOFTWARE }

data class PortSignature(
    val inputs: List<String>,
    val outputs: List<String>,
    val self: List<String>
) {
    fun hasPort(port: String): Boolean =
        inputs.contains(port) || outputs.contains(port) || self.contains(port)
}

data class NodeDefinition(
    val name: String,
    val type: NodeType,
    val ports: PortSignature
)

data class Endpoint(
    val node: String,
    val port: String
)

data class Connection(
    val from: Endpoint,
    val to: Endpoint
)

data class NetworkTopology(
    val nodes: List<NodeDefinition>,
    val connections: List<Connection>
)

class ValidationException(message: String) : RuntimeException(message)

fun formatTopology(topology: NetworkTopology): String {
    val sb = StringBuilder()

    sb.appendLine("Nodes:")
    topology.nodes.forEach { node ->
        sb.append(" - ")
            .append(node.name)
            .append(" (")
            .append(node.type.name.lowercase())
            .append("): ")
            .append("in=${node.ports.inputs}, out=${node.ports.outputs}, self=${node.ports.self}")
            .appendLine()
    }

    sb.appendLine("Connections:")
    if (topology.connections.isEmpty()) {
        sb.appendLine(" - (none)")
    } else {
        topology.connections.forEach { connection ->
            sb.append(" - ")
                .append(connection.from.node)
                .append('.')
                .append(connection.from.port)
                .append(" -> ")
                .append(connection.to.node)
                .append('.')
                .append(connection.to.port)
                .appendLine()
        }
    }

    return sb.toString()
}

fun analyzeProgram(program: SolaceParser.ProgramContext): NetworkTopology =
    NetworkVisitor().analyze(program)

class NetworkVisitor : SolaceBaseVisitor<Unit>() {
    private data class NodeInfo(
        val definition: NodeDefinition,
        val ctx: SolaceParser.NodeDeclContext
    )

    private val nodeInfos = mutableListOf<NodeInfo>()
    private val nodes = mutableListOf<NodeDefinition>()
    private val connections = mutableListOf<Connection>()
    private val seenNodes = mutableSetOf<String>()
    private val seenConnections = mutableSetOf<Pair<Endpoint, Endpoint>>()
    private var hasNetwork = false
    private var nodeLookup: Map<String, NodeDefinition> = emptyMap()

    fun analyze(program: SolaceParser.ProgramContext): NetworkTopology {
        visitProgram(program)

        if (hasNetwork) {
            validateUnusedPorts()
        }

        return NetworkTopology(nodes.toList(), connections.toList())
    }

    override fun visitProgram(ctx: SolaceParser.ProgramContext?) {
        ctx ?: return

        ctx.nodeDecl().forEach { visitNodeDecl(it) }
        nodeLookup = nodes.associateBy { it.name }

        val networks = ctx.networkDecl()
        if (networks.size > 1) {
            val extraLocations = networks.drop(1).joinToString { location(it) }
            throw ValidationException("Only one network declaration is allowed (extra at $extraLocations).")
        }

        val network = networks.singleOrNull()
        if (network != null) {
            hasNetwork = true
            visitNetworkDecl(network)
        }
    }

    override fun visitNodeDecl(ctx: SolaceParser.NodeDeclContext?) {
        ctx ?: return

        val name = ctx.ID().text
        if (!seenNodes.add(name)) {
            throw ValidationException("Duplicate node '$name' at ${location(ctx)}.")
        }

        val type = when {
            ctx.HARDWARE() != null -> NodeType.HARDWARE
            ctx.SOFTWARE() != null -> NodeType.SOFTWARE
            else -> error("Unknown node kind at ${location(ctx)}.")
        }

        val signature = parseChannelSignature(name, ctx.channelSignature())
        val definition = NodeDefinition(name, type, signature)
        nodeInfos += NodeInfo(definition, ctx)
        nodes += definition
    }

    override fun visitNetworkDecl(ctx: SolaceParser.NetworkDeclContext?) {
        ctx ?: return
        ctx.connection().forEach { visitConnection(it) }
    }

    override fun visitConnection(ctx: SolaceParser.ConnectionContext?) {
        ctx ?: return
        val endpoints = ctx.endpoint()
        val from = toEndpoint(endpoints[0])
        val to = toEndpoint(endpoints[1])

        validateEndpoint(from, ctx)
        validateEndpoint(to, ctx)

        val key = Pair(from, to)
        if (!seenConnections.add(key)) {
            throw ValidationException("Duplicate connection ${from.node}.${from.port} -> ${to.node}.${to.port} (${location(ctx)}).")
        }

        connections += Connection(from, to)
    }

    private fun parseChannelSignature(
        nodeName: String,
        signature: SolaceParser.ChannelSignatureContext?
    ): PortSignature {
        val inputs = linkedSetOf<String>()
        val outputs = linkedSetOf<String>()
        val selfPorts = linkedSetOf<String>()

        if (signature != null) {
            for (clause in signature.channelClause()) {
                val target = when {
                    clause.IN() != null -> inputs
                    clause.OUT() != null -> outputs
                    clause.SELF() != null -> selfPorts
                    else -> error("Unknown channel clause at ${location(clause)}.")
                }

                for (id in clause.idList().ID()) {
                    val port = id.text
                    if (port in inputs || port in outputs || port in selfPorts) {
                        throw ValidationException("Duplicate port '$port' in node '$nodeName' (${location(clause)}).")
                    }
                    target.add(port)
                }
            }
        }

        return PortSignature(inputs.toList(), outputs.toList(), selfPorts.toList())
    }

    private fun validateEndpoint(
        endpoint: Endpoint,
        ctx: ParserRuleContext
    ) {
        val node = nodeLookup[endpoint.node]
            ?: throw ValidationException("Unknown node '${endpoint.node}' in network (${location(ctx)}).")

        if (!node.ports.hasPort(endpoint.port)) {
            throw ValidationException("Node '${endpoint.node}' has no port '${endpoint.port}' (${location(ctx)}).")
        }
    }

    private fun toEndpoint(ctx: SolaceParser.EndpointContext): Endpoint {
        val ids = ctx.ID()
        check(ids.size == 2) { "Expected endpoint to have 2 identifiers at ${location(ctx)}." }
        return Endpoint(ids[0].text, ids[1].text)
    }

    private fun validateUnusedPorts() {
        val usedByNode = mutableMapOf<String, MutableSet<String>>()

        connections.forEach { connection ->
            usedByNode.getOrPut(connection.from.node) { mutableSetOf() }.add(connection.from.port)
            usedByNode.getOrPut(connection.to.node) { mutableSetOf() }.add(connection.to.port)
        }

        val unused = mutableListOf<String>()
        for (nodeInfo in nodeInfos) {
            val node = nodeInfo.definition
            val declared = (node.ports.inputs + node.ports.outputs + node.ports.self).toSet()
            val used = mutableSetOf<String>().apply {
                addAll(usedByNode[node.name].orEmpty())
                addAll(collectPortUsages(nodeInfo.ctx, declared))
            }
            val unusedPorts = declared - used
            if (unusedPorts.isNotEmpty()) {
                unused += "node '${node.name}': ${unusedPorts.joinToString(", ")}"
            }
        }

        if (unused.isNotEmpty()) {
            throw ValidationException("Unused ports: ${unused.joinToString("; ")}.")
        }
    }

    private fun collectPortUsages(ctx: SolaceParser.NodeDeclContext, ports: Set<String>): Set<String> {
        val used = mutableSetOf<String>()

        fun visit(node: ParseTree) {
            when (node) {
                is SolaceParser.FifoWriteStmtContext -> {
                    val port = node.ID().text
                    if (port in ports) {
                        used.add(port)
                    }
                }
                is SolaceParser.HardwareFifoWriteStmtContext -> {
                    val port = node.ID().text
                    if (port in ports) {
                        used.add(port)
                    }
                }
                is SolaceParser.FifoReadExprContext -> {
                    val port = node.ID().text
                    if (port in ports) {
                        used.add(port)
                    }
                }
            }

            for (i in 0 until node.childCount) {
                visit(node.getChild(i))
            }
        }

        visit(ctx)
        return used
    }

    private fun location(ctx: ParserRuleContext): String {
        val token = ctx.start
        return "line ${token.line}:${token.charPositionInLine + 1}"
    }
}
