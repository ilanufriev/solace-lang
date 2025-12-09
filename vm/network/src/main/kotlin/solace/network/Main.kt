package solace.network

import java.nio.file.Path
import java.nio.file.Files

private data class CliOptions(
    val packagePath: Path,
    val sniff: Boolean,
    val useSimVm: Boolean,
    val useHarvVm: Boolean,
    val useMixedVm: Boolean,
    val durationMs: Long?,
    val sniffLimit: Int?,
    val sniffCsv: Boolean,
    val sniffCsvFile: Path?,
    val dotFile: Path?
)

private fun parseArgs(args: Array<String>): CliOptions {
    var path: Path? = null
    var sniff = false
    var useSimVm = false
    var useHarvVm = false
    var useMixedVm = false
    var durationMs: Long? = null
    var sniffLimit: Int? = null
    var sniffCsv = false
    var sniffCsvFile: Path? = null
    var dotFile: Path? = null
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--sniff" -> sniff = true
            "--sim" -> useSimVm = true
            "--harv" -> useHarvVm = true
            "--mixed" -> useMixedVm = true
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
            "--dot-out" -> {
                i++
                if (i >= args.size) error("Missing value for $arg")
                dotFile = Path.of(args[i])
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
    val modeCount = listOf(useSimVm, useHarvVm, useMixedVm).count { it }
    require(modeCount <= 1) { "Flags --sim, --harv, and --mixed are mutually exclusive" }
    val packagePath = path ?: Path.of("compiler/build/solace/pseudocode.solpkg")
    return CliOptions(packagePath, sniff, useSimVm, useHarvVm, useMixedVm, durationMs, sniffLimit, sniffCsv, sniffCsvFile, dotFile)
}

fun main(args: Array<String>) {
    val options = try {
        parseArgs(args)
    } catch (ex: IllegalStateException) {
        println("Usage: solace-network <program.solpkg> [--dot-out <file>] [--sniff] [--sniff-csv] [--sniff-csv-file <path>] [--sim] [--sniff-limit <n>] [--duration-ms <n>] [--help]")
        println("Options:")
        println("  --dot-out <file>       Write network topology to Graphviz DOT file and exit")
        println("  --sniff                Print channel traffic for all connections")
        println("  --sniff-csv            Log traffic as CSV: from_node,from_port,to_node,to_port,value (implies --sniff)")
        println("  --sniff-csv-file <p>   Write sniffed CSV records into file <p> (implies --sniff-csv)")
        println("  --sniff-limit <n>      Max messages to log per connection when sniffing (mute after n)")
        println("  --sim                  Run all nodes with the simulator VM instead of stub VM")
        println("  --harv                 Run software nodes with the Harv VM instead of stub VM (software-only packages)")
        println("  --mixed                Run hardware nodes with simulator VM and software nodes with Harv VM (default)")
        println("  --duration-ms <n>      Stop the network after n milliseconds (default when sniffing: 5000)")
        println("  --help,-h              Show this help")
        return
    }

    val program = loadProgramPackage(options.packagePath)
    if (options.useHarvVm) {
        val hardwareNodes = program.nodes.filter { it.type == NodeType.HARDWARE }
        require(hardwareNodes.isEmpty()) {
            "--harv can only run software-only packages; hardware nodes found: ${hardwareNodes.joinToString { it.name }}"
        }
    }
    if (options.dotFile != null) {
        val dotNetwork = buildNetwork(program).toDOTNetwork().toString()
        options.dotFile.parent?.let { Files.createDirectories(it) }
        Files.writeString(options.dotFile, dotNetwork)
        println("DOT graph written to ${options.dotFile.toAbsolutePath()}")
        return
    }

    val factory = when {
        options.useMixedVm -> MixedNodeVmFactory()
        options.useHarvVm -> HarvNodeVmFactory()
        options.useSimVm -> SimNodeVmFactory()
        else -> MixedNodeVmFactory() // default mixed: simulator for hardware, Harv for software
    }

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
