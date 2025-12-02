# Running and implementing node VMs (network module)

Подробная инструкция, чтобы сеть поднялась и узловые ВМ заработали.

## 1. Запуск готовой сети

- Соберите пакет `*.solpkg` (формат см. `FILE_FORMATS.md`).
- Запустите: `./gradlew :vm:network:run --args "path/to/program.solpkg"`.
- По умолчанию используется `StubNodeVmFactory`: проверяет разводку портов и раз в `logIntervalMs` печатает heartbeat.
- Чтобы увидеть разводку каналов, включите лог портов:
  - через helper: `runNetwork(program, logPorts = true)`
  - или напрямую: `BuiltNetwork.launch(scope, StubNodeVmFactory(logIntervalMs, logPorts = true))`

## 2. Правила портов (валидируются)

- Каждый объявленный `input` и `output` должен иметь канал.
- Каждый `self` порт обязан быть одним и тем же каналом во всех трёх местах: `inputs["loop"] === outputs["loop"] === self["loop"]`.
- Двойные подключения запрещены (один вход/выход — один канал).
- При нарушении `StubNodeVmFactory.create(...)` бросит `IllegalArgumentException` с пояснением. С `logPorts = true` видно идентификаторы каналов.

## 3. Как написать свою ВМ

1) Реализуйте `NodeVmFactory`, создающую `NodeVm` по `NetworkNode`.
2) В `NodeVm.launch(scope)` опишите логику:
   - Чтение: `for (msg in node.ports.inputs["in"]) { ... }`
   - Запись: `node.ports.outputs["out"]?.send(value)`
   - Self loop: отправили в `outputs["loop"]` — получите в `inputs["loop"]` (тот же канал).
3) Подставьте фабрику:
   - `BuiltNetwork.launch(scope, yourFactory)`
   - или добавьте параметр фабрики в ваш helper (по аналогии с `runNetwork`).

Минимальный пример фабрики для логов трафика:

```kotlin
class LoggingVmFactory : NodeVmFactory {
    override fun create(node: NetworkNode): NodeVm = object : NodeVm {
        override fun launch(scope: CoroutineScope): Job = scope.launch {
            node.ports.inputs.forEach { (name, ch) ->
                launch { for (msg in ch) println("[IN  ${node.descriptor.name}.$name] $msg") }
            }
            node.ports.outputs["out"]?.send("ping") // пример отправки
        }
    }
}
```

## 4. Проверка и тесты

- Юнит-тесты: `./gradlew :vm:network:test` — проверяют self каналы и валидацию фабрики.
- Ручная проверка разводки: `logPorts = true` и смотрите одинаковые hex ID для self канала в inputs/outputs/self.

Это покрывает каркас сети. Ваша реальная логика исполнения (hardware/software) живёт в `NodeVm` и использует предоставленные каналы. Пока интерпретатора нет — используйте `StubNodeVmFactory` или диагностическую фабрику для дымовых тестов.***
