package solace.network

// Factory that routes hardware nodes to simulator VM and software nodes to Harv VM.
class MixedNodeVmFactory : NodeVmFactory {
    private val simFactory = SimNodeVmFactory()
    private val harvFactory = HarvNodeVmFactory()

    override fun create(node: NetworkNode): NodeVm =
        when (node.descriptor.type) {
            NodeType.HARDWARE -> simFactory.create(node)
            NodeType.SOFTWARE -> harvFactory.create(node)
        }
}
