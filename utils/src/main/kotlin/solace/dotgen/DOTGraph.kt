package solace.utils.dotgen;


class DOTConnection(
    val sourceNodeName: String,
    val sourcePortName: String,
    val destinationNodeName: String,
    val destinationPortName: String
) {
    fun getString(): String {
        return if (sourceNodeName == destinationNodeName && sourcePortName == destinationPortName) {
            "$sourceNodeName -> $destinationNodeName [label=\"$sourcePortName\"];"
        } else {
            "$sourceNodeName -> $destinationNodeName [taillabel=\"$sourcePortName\",headlabel=\"$destinationPortName\"];"
        }
    }
}


class DOTNetwork(val connections: Array<DOTConnection>) {
    fun getString(): String {
        return """
            digraph Network {
                node [shape=rect,color=gray];
                edge [color=gray];
                
                ${(connections.map { it.getString() }).reduce { acc, cstr -> acc + cstr }}
            }
        """.trimIndent()
    }
}