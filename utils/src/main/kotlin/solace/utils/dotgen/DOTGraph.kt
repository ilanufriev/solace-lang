package solace.utils.dotgen

import java.util.*


class DNP (
    val node: String,
    val port: String
) {
    override fun toString(): String = node + "_" + port
    override fun equals(other: Any?): Boolean = (other is DNP) && node == other.node && port == other.port
    override fun hashCode(): Int = Objects.hash(node) + Objects.hash(port)
}


class DOTConnection(
    val source: DNP,
    val destination: DNP
) {
    override fun toString(): String =
        if (source.node == destination.node && source.port == destination.port) {
            "${source.node} -> ${destination.node} [label=<table>\"${source.port}\"</table>];"
        } else {
            "${source.node} -> ${destination.node} [taillabel=\"${source.port}\",headlabel=\"${destination.port}\"];"
        }
}


class DOTNetwork(val connections: List<DOTConnection>) {
    override fun toString(): String = (
        arrayOf(
            "digraph Network {\n",
            "\tnode [shape=rect,color=gray];\n",
            "\tedge [color=gray];\n\n"
        ) + ((connections.groupBy { it.source }).toList().map { (key, value) ->
            if (value.size == 1) arrayOf("\t${value[0].toString()}\n")
            else arrayOf(
                "\n\tsubgraph helper_$key {\n",
                "\t\tnode [shape=circle,width=0,label=\"\",color=gray];\n",
                "\t\thelper_$key;\n",
                "\t}\n\n",
                "\t${value[0].source.node} -> helper_${key} [arrowhead=none,taillabel=\"${value[0].source.port}\"];\n"
            ) + (value.map { "\thelper_${key} -> ${it.destination.node} [headlabel=${it.destination.port}];\n" })
        }).reduce { acc, sublist -> acc + sublist } + "}"
    ).reduce { acc, cstr -> acc + cstr }
}