package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.Constants;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.SimulatorMemory;

public class Eval extends Instruction {
    public Eval(long operand) {
        super(operand);
    }

    @Override
    public void execute(SimulatorMemory vm) {
        int op = (int) operand;
        if (op == Constants.EVAL_CONDITION) {
            long falseTarget = vm.getOperandStack().pop(); // 栈顶
            long trueTarget = vm.getOperandStack().pop(); // 中间
            long cond = vm.getOperandStack().pop(); // 栈底
            if (cond != 0) {
                vm.setProgramCounter(trueTarget);
            } else {
                vm.setProgramCounter(falseTarget);
            }
            // 不要调用instructionDone！因为我们已经手动设置了PC！
        } else {
            long right = vm.getOperandStack().pop();
            long left = vm.getOperandStack().pop();
            long result;
            switch (op) {
                case Constants.EVAL_ADD:
                    result = left + right;
                    break;
                case Constants.EVAL_MINUS:
                    result = left - right;
                    break;
                case Constants.EVAL_MUL:
                    result = left * right;
                    break;
                case Constants.EVAL_DIV:
                    result = left / right;
                    break;
                case Constants.EVAL_MOD:
                    result = left % right;
                    break;
                case Constants.EVAL_GREATER:
                    result = left > right ? 1L : 0L;
                    break;
                case Constants.EVAL_LESS:
                    result = left < right ? 1L : 0L;
                    break;
                case Constants.EVAL_GREATER_OR_EQUAL:
                    result = left >= right ? 1L : 0L;
                    break;
                case Constants.EVAL_LESS_OR_EQUAL:
                    result = left <= right ? 1L : 0L;
                    break;
                case Constants.EVAL_EQUAL:
                    result = left == right ? 1L : 0L;
                    break;
                case Constants.EVAL_NOT_EQUAL:
                    result = left != right ? 1L : 0L;
                    break;
                default:
                    throw new IllegalStateException("Unexpected eval operand: 0x" + Integer.toHexString(op));
            }
            vm.getOperandStack().push(result);
            vm.instructionDone();
        }
    }

    @Override
    public String toString() {
        return "Eval(" + Long.toHexString(operand) + ")";
    }
}
