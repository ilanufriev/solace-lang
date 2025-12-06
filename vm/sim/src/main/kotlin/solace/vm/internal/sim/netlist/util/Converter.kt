package solace.vm.internal.sim.netlist.util

object Converter {
    fun booleanToInt(value: Boolean): Int {
        return if (value) 1 else 0
    }

    fun intToBoolean(value: Int): Boolean {
        return if (value == 0) false else true
    }
}