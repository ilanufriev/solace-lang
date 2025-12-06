package solace.vm.internal.sim.netlist.util

import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.memberProperties

object PortSentinel {
    fun portsConnected(obj: Any): Boolean {
        var retval = true
        val objclass = obj::class

        for (memb in objclass.memberProperties) {
            val value = memb.call(obj)

            val isPort = memb.findAnnotations<Port>().isNotEmpty();
            val isPortArray = if (isPort) false
            else memb.findAnnotations<PortArray>().isNotEmpty()

            // In JVM Array type has a privilege of partially preserving
            // the type of values it is holding. This is why we only use
            // arrays for inputs and outputs
            if (isPortArray && value is Array<*>) {
                for (i in value.indices) {
                    if (value[i] == null) {
                        retval = false

                        System.err.printf(
                            "%s's wire with index = %d in array %s is not connected to anything!\n",
                            objclass.simpleName, i, memb.name
                        )
                    }
                }
            }

            if (isPort && (value == null)) {
                retval = false

                System.err.printf(
                    "%s's wire %s is not connected to anything!\n",
                    objclass.simpleName, memb.name
                )
            }
        }

        return retval
    }
}