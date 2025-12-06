package solace.utils.dotgen

import solace.utils.dotgen.*

class App {
    val greeting: String
        get() {
            /*
                .con $counter@out $add@in1
                .immcon $add@in2 #1
                .con $add@out $x@in
                .con $x@out $numbers@in
                .con $x@out $counter@in
             */

            return DOTNetwork(arrayOf<DOTConnection>(
                DOTConnection("counter", "out", "add", "in1"),
                DOTConnection("1", "", "add", "in2"),
                DOTConnection("add", "out", "x", "in"),
                DOTConnection("x", "out", "numbers", "in"),
                DOTConnection("x", "out", "counter", "in")
            )).getString()
        }
}

fun main() {
    val a = App(); System.err.print(a.greeting);
}
