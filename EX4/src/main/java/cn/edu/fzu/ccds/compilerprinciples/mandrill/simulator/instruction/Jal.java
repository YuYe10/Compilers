package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

/**
 * jal x: 链接跳转（函数调用）。
 * 1. 保存当前 SP 和 PC（返回地址为下一条指令）。
 * 2. 弹出栈顶元素作为局部变量区字节数，申请堆内存。
 * 3. 将操作数栈上的参数复制到新分配的内存区域。
 * 4. SP 设置为新内存起始地址。
 * 5. PC 设置为 x。
 */
public class Jal extends Instruction {
    public Jal(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        long localSize = vm.getOperandStack().pop();
        long returnAddress = vm.getProgramCounter() + 8;
        
        int paramCount = vm.getOperandStack().size();
        long[] params = new long[paramCount];
        for (int i = 0; i < paramCount; i++) {
            params[i] = vm.getOperandStack().pop();
        }
        
        vm.pushCallFrame(vm.getStackPointer(), returnAddress);
        long newSp = vm.getHeap().allocate((int) localSize);
        
        for (int i = 0; i < paramCount; i++) {
            vm.getHeap().write(newSp + (long) (paramCount - 1 - i) * 4, params[i]);
        }
        
        vm.setStackPointer(newSp);
        vm.setProgramCounter(operand);
    }
}
