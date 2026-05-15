package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

public class Pop extends Instruction {
    public Pop(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        vm.getOperandStack().pop();
        vm.instructionDone();
    }
}
