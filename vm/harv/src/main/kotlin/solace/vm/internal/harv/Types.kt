package solace.vm.internal.harv

import jdk.jshell.spi.ExecutionControlProvider

interface HarvVal
class HarvInt(var value: Int) : HarvVal {
    companion object staticData {
        const val typeName = "int"
    }
}
class HarvString(var value: String) : HarvVal {
    companion object staticData {
        const val typeName = "string"
    }
}
class HarvIdentifier(var value: String) : HarvVal {}
class HarvFifo(var name: String) : HarvVal {
    class HarvFifoIsEmpty() : Exception("Fifo is empty")

    var queue = mutableListOf<Int>()

    companion object staticData {
        const val typeName = "fifo"
    }

    fun putToFifo(data: Int) {
        queue.addLast(data)
    }

    fun pushFromFifo(): Int {
        if (queue.isEmpty()) {
            throw HarvFifoIsEmpty()
        }

        return queue.removeFirst()
    }
}

typealias HarvStack = List<HarvVal>
