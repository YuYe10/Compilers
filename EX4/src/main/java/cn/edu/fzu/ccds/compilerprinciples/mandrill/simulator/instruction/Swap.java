package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

public class Swap extends Instruction {
    public Swap(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        long top = vm.getOperandStack().pop();
        long second = vm.getOperandStack().pop();
        vm.getOperandStack().push(top);
        vm.getOperandStack().push(second);
        vm.instructionDone();
    }
}