package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

/**
 * dlwrite x: 将操作数栈顶元素弹出，写入局部变量区（SP + x*4）。
 */
public class DLWrite extends Instruction {
    public DLWrite(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        System.err.println("[DEBUG] DLWrite: operandStack size = " + vm.getOperandStack().size());
        for (long v : vm.getOperandStack()) {
            System.err.println("[DEBUG]  - value: " + v);
        }
        long value = vm.getOperandStack().pop();
        vm.writeLocal((int) operand, value);
        vm.instructionDone();
    }
}
