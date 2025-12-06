package solace.dotgen


class App {
    val greeting: String
        get() {
            /*
                Input:

                .con $counter@out $add@in1
                .immcon $add@in2 #1
                .con $add@out $x@in
                .con $x@out $numbers@in
                .con $x@out $counter@in
             */

            return DOTNetwork(arrayOf<DOTConnection>(
                DOTConnection(DNP("counter", "out"), DNP("add", "in1")),
                DOTConnection(DNP("1", ""), DNP("add", "in2")),
                DOTConnection(DNP("add", "out"), DNP("x", "in")),
                DOTConnection(DNP("x", "out"), DNP("numbers", "in")),
                DOTConnection(DNP("x", "out"), DNP("counter", "in"))
            )).toString()

            /*
                Output:

                digraph Network {
                        node [shape=rect,color=gray];
                        edge [color=gray];

                        counter -> add [taillabel="out",headlabel="in1"];
                        1 -> add [taillabel="",headlabel="in2"];
                        add -> x [taillabel="out",headlabel="in"];

                        subgraph helper_x_out {
                                node [shape=circle,width=0,label="",color=gray];
                                helper_x_out;
                        }

                        x -> helper_x_out [arrowhead=none,taillabel="out"];
                        helper_x_out -> numbers [headlabel=in];
                        helper_x_out -> counter [headlabel=in];
                }
             */
        }
}

fun main() {
    val a = App(); System.err.print(a.greeting);
}
