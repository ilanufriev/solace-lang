package solace.vm.internal.harv

interface ReadableProgramMemory {
    fun readNextInt(): Int
    fun readNextUint(): UInt

    fun seek(adr: UInt)
    fun reset()

    fun isEof(): Boolean
}