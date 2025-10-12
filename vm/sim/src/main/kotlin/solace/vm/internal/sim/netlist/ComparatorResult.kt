package solace.vm.internal.sim.netlist

enum class ComparatorResult(val code: Int) {
    EQUAL(0), LESS(-1), GREATER(1)
}