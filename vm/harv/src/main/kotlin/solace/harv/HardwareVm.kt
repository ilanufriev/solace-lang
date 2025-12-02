package solace.harv

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch

/**
 * Каркас hardware-ВМ.
 * Считывает контейнер solbc, делит на init/run и предоставляет скелетные методы
 * для будущей интерпретации комбинаторных опкодов.
 * Добавлена поддержка FIFO-портов через корутины и каналы.
 */
class HardwareVm(
    private val bytecode: ByteArray,
    private val ports: Ports
) {
    private val sections = splitSolbc(bytecode)

    /**
    * Запуск в корутине: init один раз, затем бесконечный run-цикл.
    * Реальный интерпретатор должен вызвать runInit(), потом крутить runLoop()
    * с обращением к портам/каналам.
    */
    fun launch(scope: CoroutineScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default): Job =
        scope.launch(dispatcher) {
            runInit()
            while (true) {
                runLoop()
                kotlinx.coroutines.yield() // кооперация
            }
        }

    private suspend fun runInit() {
        // TODO: интерпретировать sections.init, используя fifoRead/fifoWrite при необходимости
    }

    private suspend fun runLoop() {
        // TODO: интерпретировать sections.run (комбинаторная логика)
        // Пример обращения к портам:
        // val v = fifoRead("in")
        // fifoWrite("out", v)
    }

    private suspend fun fifoRead(port: String): Any? =
        ports.inputs[port]?.receive() ?: error("Input port '$port' not found")

    private suspend fun fifoWrite(port: String, value: Any?) {
        ports.outputs[port]?.send(value) ?: error("Output port '$port' not found")
    }

    data class Ports(
        val inputs: Map<String, ReceiveChannel<Any?>>,
        val outputs: Map<String, SendChannel<Any?>>,
        val self: Map<String, Channel<Any?>>
    )

    private data class Sections(val init: ByteArray, val run: ByteArray)

    private fun splitSolbc(bytes: ByteArray): Sections {
        require(bytes.size >= 16) { "solbc too small: ${bytes.size}" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }.decodeToString()
        require(magic == "SOLB") { "Invalid solbc magic: '$magic'" }
        buffer.get() // container_version
        buffer.get() // node_type
        buffer.get() // isa_version
        buffer.get() // flags
        val initSize = buffer.int
        val runSize = buffer.int
        val initStart = 16
        val runStart = initStart + initSize
        require(initSize >= 0 && runSize >= 0 && runStart + runSize <= bytes.size) {
            "Invalid section sizes: init=$initSize, run=$runSize, size=${bytes.size}"
        }
        return Sections(
            init = bytes.copyOfRange(initStart, runStart),
            run = bytes.copyOfRange(runStart, runStart + runSize)
        )
    }
}
