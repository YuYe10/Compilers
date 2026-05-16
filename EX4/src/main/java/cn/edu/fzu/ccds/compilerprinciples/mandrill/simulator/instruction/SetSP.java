package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

/**
 * setsp x: 将栈顶元素弹出，设置为新的栈指针 SP。
 */
public class SetSP extends Instruction {
    public SetSP(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        long newSp = vm.getOperandStack().pop();
        vm.setStackPointer(newSp);
        vm.instructionDone();
    }
}
