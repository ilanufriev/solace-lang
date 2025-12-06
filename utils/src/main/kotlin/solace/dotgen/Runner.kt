package solace.utils.dotgen

import solace.utils.dotgen.*

class App {
    val greeting: String
        get() {
            return DOTNetwork(arrayOf<DOTConnection>(
                DOTConnection("a", "out", "b", "in")
            )).getString()
        }
}

fun main() {
    val a = App(); System.err.print(a.greeting);
}
