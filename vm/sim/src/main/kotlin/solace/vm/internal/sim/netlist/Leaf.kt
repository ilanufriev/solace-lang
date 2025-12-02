package solace.vm.internal.sim.netlist

abstract class Leaf<T> {
    abstract val ports: MutableMap<String, Wire<T>?>

    private fun getNoPortException(portName: String): IllegalArgumentException {
        return IllegalArgumentException("There is no port \"$portName\"")
    }

    fun portExists(portName: String): Boolean {
        return ports.containsKey(portName)
    }

    @Throws(IllegalArgumentException::class)
    fun getPort(name: String): Wire<T> {
        if (portExists(name)) {
            return ports[name]!!
        }

        throw getNoPortException(name)
    }

    @Throws(IllegalArgumentException::class)
    fun connectPort(name: String, wire: Wire<T>?) {
        if (portExists(name)) {
            ports[name] = wire
            return
        }

        throw getNoPortException(name)
    }

    @Throws(IllegalArgumentException::class)
    fun isPortConnected(name: String): Boolean {
        if (portExists(name)) {
            return ports[name] != null
        }

        throw getNoPortException(name)
    }

    abstract fun evaluate()
}
