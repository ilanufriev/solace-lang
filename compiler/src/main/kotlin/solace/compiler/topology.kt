package solace.compiler

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
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

private data class NodeInfo(
    val definition: NodeDefinition,
    val ctx: SolaceParser.NodeDeclContext
)

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

class ValidationException(message: String) : RuntimeException(message)

fun analyzeProgram(program: SolaceParser.ProgramContext): NetworkTopology {
    val nodeInfos = parseNodes(program)
    val nodes = nodeInfos.map { it.definition }
    val hasNetwork = program.networkDecl().isNotEmpty()
    val nodeLookup = nodes.associateBy { it.name }
    val connections = parseConnections(program, nodeLookup)

    if (hasNetwork) {
        validateUnusedPorts(nodeInfos, connections)
    }

    return NetworkTopology(nodes, connections)
}

private fun parseNodes(program: SolaceParser.ProgramContext): List<NodeInfo> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<NodeInfo>()

    for (nodeCtx in program.nodeDecl()) {
        val name = nodeCtx.ID().text
        if (!seen.add(name)) {
            throw ValidationException("Duplicate node '$name' at ${location(nodeCtx)}.")
        }

        val type = when {
            nodeCtx.HARDWARE() != null -> NodeType.HARDWARE
            nodeCtx.SOFTWARE() != null -> NodeType.SOFTWARE
            else -> error("Unknown node kind at ${location(nodeCtx)}.")
        }

        val signature = parseChannelSignature(name, nodeCtx.channelSignature())
        result += NodeInfo(NodeDefinition(name, type, signature), nodeCtx)
    }

    return result
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

private fun parseConnections(
    program: SolaceParser.ProgramContext,
    nodes: Map<String, NodeDefinition>
): List<Connection> {
    if (program.networkDecl().isEmpty()) {
        return emptyList()
    }
    if (program.networkDecl().size > 1) {
        val extraLocations = program.networkDecl().drop(1).joinToString { location(it) }
        throw ValidationException("Only one network declaration is allowed (extra at $extraLocations).")
    }

    val result = mutableListOf<Connection>()
    val networkCtx = program.networkDecl().single()
    val seenConnections = mutableSetOf<Pair<Endpoint, Endpoint>>()

    for (connectionCtx in networkCtx.connection()) {
        val endpoints = connectionCtx.endpoint()
        val from = toEndpoint(endpoints[0])
        val to = toEndpoint(endpoints[1])

        validateEndpoint(from, nodes, connectionCtx)
        validateEndpoint(to, nodes, connectionCtx)

        val key = Pair(from, to)
        if (!seenConnections.add(key)) {
            throw ValidationException("Duplicate connection ${from.node}.${from.port} -> ${to.node}.${to.port} (${location(connectionCtx)}).")
        }

        result += Connection(from, to)
    }

    return result
}

private fun validateEndpoint(
    endpoint: Endpoint,
    nodes: Map<String, NodeDefinition>,
    ctx: ParserRuleContext
) {
    val node = nodes[endpoint.node]
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

private fun validateUnusedPorts(nodes: List<NodeInfo>, connections: List<Connection>) {
    val usedByNode = mutableMapOf<String, MutableSet<String>>()

    connections.forEach { connection ->
        usedByNode.getOrPut(connection.from.node) { mutableSetOf() }.add(connection.from.port)
        usedByNode.getOrPut(connection.to.node) { mutableSetOf() }.add(connection.to.port)
    }

    val unused = buildList {
        for (nodeInfo in nodes) {
            val node = nodeInfo.definition
            val declared = (node.ports.inputs + node.ports.outputs + node.ports.self).toSet()
            val used = mutableSetOf<String>().apply {
                addAll(usedByNode[node.name].orEmpty())
                addAll(collectPortUsages(nodeInfo.ctx, declared))
            }
            val unusedPorts = declared - used
            if (unusedPorts.isNotEmpty()) {
                add("node '${node.name}': ${unusedPorts.joinToString(", ")}")
            }
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
