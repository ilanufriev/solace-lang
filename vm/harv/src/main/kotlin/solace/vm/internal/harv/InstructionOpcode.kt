package solace.vm.internal.harv

enum class InstructionOpcode(val code: UInt) {
    // Базовые операции
    PUSH(0x01u),    // [] -> [<Argument>]
    POP(0x02u),     // [x] -> []

    // Математика
    ADD(0x03u),         // [a, b] -> [a + b]
    SUBTRACT(0x04u),    // [a, b] -> [a - b]
    MULTIPLY(0x05u),    // [a, b] -> [a * b]
    DIVIDE(0x06u),      // a, b] -> [a / b]
    MOD(0x07u),         // [a, b] -> [a % b]
    NEGATIVE(0x08u),    // [x] -> [-x]
    DUPLICATE(0x09u),   // [x] -> [x, x]
    SWAP(0x0Au),        // [a, b] -> [b, a]

    // Операции ветвления
    JUMP(0x10u),            // jump to <Argument>
    JUMP_IF_TRUE(0x11u),    // jump to <Argument> if [x] != 0 ; [x] -> []

    // Операции сравнения
    EQUAL(0x20u),               //  [a, b] -> [a == b]
    NOT_EQUAL(0x21u),           //  [a, b] -> [a != b]
    LESS_THAN(0x22u),           //  [a, b] -> [a < b]
    GREATER_THAN(0x23u),        //  [a, b] -> [a > b]
    LESS_OR_EQUAL(0x24u),       //  [a, b] -> [a <= b]
    GREATER_OR_EQUAL(0x25u),    //  [a, b] -> [a >= b]
    LOGICAL_AND(0x26u),         //  [a, b] -> [a && b]
    LOGICAL_OR(0x27u),          //  [a, b] -> [a || b]
    LOGICAL_NOT(0x28u),         //  [x] -> [!x]

    // Специальные
    NOP(0x00u);
}

