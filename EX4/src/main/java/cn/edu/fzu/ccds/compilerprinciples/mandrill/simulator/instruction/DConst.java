package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

public class DConst extends Instruction {
    public DConst(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        vm.getOperandStack().push(operand);
        vm.instructionDone();
    }
}
