package solace.vm.internal.sim.test.netlist

import kotlin.test.Test
import kotlin.test.assertEquals
import solace.vm.internal.sim.netlist.*

class ComparatorTest {
    @Test fun testComparator() {
        val comp: Comparator = Comparator(null, null, null);
        comp.in1 = Wire<Int>();
        comp.in2 = Wire<Int>();
        comp.out = Wire<Int>();

        comp.in1!!.send(1);
        comp.in2!!.send(2);

        comp.evaluate()

        assertEquals(ComparatorResult.LESS.code, comp.out!!.receive() ?: 0, "1 is LESS than 2, should be -1")

        comp.in1!!.send(2);
        comp.in2!!.send(1);

        comp.evaluate()

        assertEquals(ComparatorResult.GREATER.code, comp.out!!.receive() ?: 0, "2 is GREATER than 1, should be 1")

        comp.in1!!.send(2);
        comp.in2!!.send(2);

        comp.evaluate()

        assertEquals(ComparatorResult.EQUAL.code, comp.out!!.receive() ?: 0, "2 is EQUAL to 2, should be 0")
    }
}