package solace.compiler

import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.Trees
import solace.compiler.antlr.SolaceParser

fun prettyTree(tree: ParseTree, parser: SolaceParser): String {
    val sb = StringBuilder()

    fun render(node: ParseTree, prefix: String, isTail: Boolean) {
        val label = Trees.getNodeText(node, parser)
        sb.append(prefix)
            .append(if (isTail) "└── " else "├── ")
            .append(label)
            .append('\n')

        val childPrefix = prefix + if (isTail) "    " else "│   "
        val lastChildIdx = node.childCount - 1
        for (i in 0..lastChildIdx) {
            render(node.getChild(i), childPrefix, i == lastChildIdx)
        }
    }

    // Root without connector, then children with ASCII branches.
    sb.append(Trees.getNodeText(tree, parser)).append('\n')
    val lastChildIdx = tree.childCount - 1
    for (i in 0..lastChildIdx) {
        render(tree.getChild(i), "", i == lastChildIdx)
    }

    return sb.toString()
}
