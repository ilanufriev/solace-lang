package solace.network

import kotlinx.coroutines.runBlocking
import java.nio.file.Path

fun main(args: Array<String>) = runBlocking {
    val packagePath = args.firstOrNull()?.let { Path.of(it) } ?: Path.of("compiler/build/solace/pseudocode.solpkg")
    val program = loadProgramPackage(packagePath)
    runNetwork(program)
}
