package solace.vm.internal.sim.types

import solace.vm.internal.sim.netlist.*

typealias DataType = Int
typealias FifoType = MutableList<DataType>
typealias LeafType = Leaf<DataType>
typealias WireType = Wire<DataType>