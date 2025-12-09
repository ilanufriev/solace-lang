package solace.examples

fun main() {
    val n = 47 // Number of Fibonacci terms to generate

    val startTime = System.nanoTime() // start time in nanoseconds

    printFibonacciSeries(n)
}

fun printFibonacciSeries(n: Int, startTime: Long = System.nanoTime()) {
    var num1 = 0
    var num2 = 1

    print("Fibonacci Series up to $n terms: ")

    for (i in 1..n) {
        println("$num1, ${System.nanoTime() - startTime}") // Print the current Fibonacci number

        val sum = num1 + num2 // Calculate the next Fibonacci number
        num1 = num2 // Update num1 to the previous num2
        num2 = sum // Update num2 to the newly calculated sum
    }
    println() // For a new line after the series
}
