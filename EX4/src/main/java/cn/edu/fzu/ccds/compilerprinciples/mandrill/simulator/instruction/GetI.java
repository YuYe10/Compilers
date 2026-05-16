package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

/**
 * geti x: 从 stdin 读入一个十进制整数，压入操作数栈。
 */
public class GetI extends Instruction {
    public GetI(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        try {
            System.err.println("GetI: trying to read...");
            if (vm.getScanner().hasNextLong()) {
                System.err.println("GetI: hasNextLong true!");
            } else {
                System.err.println("GetI: hasNextLong false!");
            }
            long value = vm.getScanner().nextLong();
            System.err.println("GetI: read value: " + value);
            vm.getOperandStack().push(value);
            System.err.println("GetI: pushed to stack, stack now: " + vm.getOperandStack());
        } catch (Exception e) {
            System.err.println("GetI: Exception occurred!");
            e.printStackTrace();
            vm.getOperandStack().push(0L);
        }
        vm.instructionDone();
    }
}
