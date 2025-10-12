package solace.vm.internal.sim.netlist

class Wire<T> {
    private var value: T? = null

    fun send(v: T) {
      value = v;
    }

    fun receive(): T? = value
  }
