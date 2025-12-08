package solace.vm.internal.harv

interface HarvVal
class HarvInt(var value: Int) : HarvVal {}
class HarvString(var value: String) : HarvVal {}
class HarvIdentifier(var value: String) : HarvVal {}
class HarvFifo(var value: String) : HarvVal {}

typealias HarvStack = List<HarvVal>
