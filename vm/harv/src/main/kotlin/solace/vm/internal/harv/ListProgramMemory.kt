package solace.vm.internal.harv

class ListProgramMemory(private var mem: List<Int>) : ReadableProgramMemory {
    private var programCounter = 0

    override fun readNextInt(): Int {
        if (programCounter >= mem.size) {
            throw IndexOutOfBoundsException("Read beyond memory bounds")
        }
        val value = mem[programCounter]
        programCounter++
        return value
    }

    override fun readNextUint(): UInt {
        if (programCounter >= mem.size) {
            throw IndexOutOfBoundsException("Read beyond memory bounds")
        }
        val value = mem[programCounter].toUInt()
        programCounter++
        return value
    }

    override fun seek(adr: UInt) {
        programCounter = adr.toInt()
    }

    override fun isEof(): Boolean {
        return programCounter >= mem.size
    }

    // Дополнительные полезные методы
    override fun reset() {
        programCounter = 0
    }

}