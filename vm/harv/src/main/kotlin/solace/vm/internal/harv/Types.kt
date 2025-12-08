package solace.vm.internal.harv

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
class HarvFifo(var value: String) : HarvVal {
    companion object staticData {
        const val typeName = "fifo"
    }
}

typealias HarvStack = List<HarvVal>
