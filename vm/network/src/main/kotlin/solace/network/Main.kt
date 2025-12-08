package solace.network

import java.nio.file.Path

private data class CliOptions(
    val packagePath: Path,
    val sniff: Boolean,
    val useSimVm: Boolean,
    val durationMs: Long?,
    val sniffLimit: Int?,
    val sniffCsv: Boolean,
    val sniffCsvFile: Path?
)

private fun parseArgs(args: Array<String>): CliOptions {
    var path: Path? = null
    var sniff = false
    var useSimVm = false
    var durationMs: Long? = null
    var sniffLimit: Int? = null
    var sniffCsv = false
    var sniffCsvFile: Path? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--sniff" -> sniff = true
            "--sim" -> useSimVm = true
            "--sniff-csv" -> {
                sniffCsv = true
                sniff = true // CSV mode implies sniffing
            }
            "--sniff-csv-file" -> {
                i++
                if (i >= args.size) error("Missing value for $arg")
                sniffCsvFile = Path.of(args[i])
                sniffCsv = true
                sniff = true
            }
            "--sniff-limit" -> {
                i++
                if (i >= args.size) error("Missing value for $arg")
                sniffLimit = args[i].toInt()
            }
            "--duration-ms" -> {
                i++
                if (i >= args.size) error("Missing value for $arg")
                durationMs = args[i].toLong()
            }
            "--help", "-h" -> throw IllegalStateException("")
            else -> {
                if (path != null) error("Unexpected argument: $arg")
                path = Path.of(arg)
            }
        }
        i++
    }
    val packagePath = path ?: Path.of("compiler/build/solace/pseudocode.solpkg")
    return CliOptions(packagePath, sniff, useSimVm, durationMs, sniffLimit, sniffCsv, sniffCsvFile)
}

fun main(args: Array<String>) {
    val options = try {
        parseArgs(args)
    } catch (ex: IllegalStateException) {
        println("Usage: solace-network <program.solpkg> [--sniff] [--sniff-csv] [--sniff-csv-file <path>] [--sim] [--sniff-limit <n>] [--duration-ms <n>] [--help]")
        println("Options:")
        println("  --sniff                Print channel traffic for all connections")
        println("  --sniff-csv            Log traffic as CSV: from_node,from_port,to_node,to_port,value (implies --sniff)")
        println("  --sniff-csv-file <p>   Write sniffed CSV records into file <p> (implies --sniff-csv)")
        println("  --sniff-limit <n>      Max messages to log per connection when sniffing (mute after n)")
        println("  --sim                  Run nodes with the simulator VM instead of stub VM")
        println("  --duration-ms <n>      Stop the network after n milliseconds (default when sniffing: 5000)")
        println("  --help,-h              Show this help")
        return
    }

    val program = loadProgramPackage(options.packagePath)
    val factory = if (options.useSimVm) {
        SimNodeVmFactory()
    } else null
    val duration = options.durationMs ?: if (options.sniff) 5_000L else null
    runNetwork(
        program,
        sniffConnections = options.sniff,
        vmFactory = factory,
        stopAfterMs = duration,
        sniffLimit = options.sniffLimit,
        sniffCsv = options.sniffCsv,
        sniffCsvFile = options.sniffCsvFile
    )
}
