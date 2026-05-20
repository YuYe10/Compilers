package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.assembler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本汇编解析器。
 * 每行一条指令，格式：<指令名> [操作数]
 * 支持 # 开头或 // 开头的注释，忽略空行。
 */
public class AssemblyParser {

    public static List<Instruction> parse(InputStream inputStream) throws IOException {
        List<Instruction> instructions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                Instruction instruction = parseLine(line);
                instructions.add(instruction);
            }
        }
        return instructions;
    }

    private static Instruction parseLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty instruction line");
        }
        String name = parts[0].toLowerCase();
        long operand = 0;
        if (parts.length >= 2) {
            operand = parseOperand(parts[1]);
        }

        if (name.equals("nop"))
            return new Nop(operand);
        else if (name.equals("dstore"))
            return new DStore(operand);
        else if (name.equals("dload"))
            return new DLoad(operand);
        else if (name.equals("dlload"))
            return new DLLoad(operand);
        else if (name.equals("daload"))
            return new DALoad(operand);
        else if (name.equals("dwrite"))
            return new DWrite(operand);
        else if (name.equals("dlwrite"))
            return new DLWrite(operand);
        else if (name.equals("dawrite"))
            return new DAWrite(operand);
        else if (name.equals("eval"))
            return new Eval(operand);
        else if (name.equals("jump"))
            return new Jump(operand);
        else if (name.equals("jal"))
            return new Jal(operand);
        else if (name.equals("ret"))
            return new Ret(operand);
        else if (name.equals("malloc"))
            return new Malloc(operand);
        else if (name.equals("geti"))
            return new GetI(operand);
        else if (name.equals("getc"))
            return new GetC(operand);
        else if (name.equals("gets"))
            return new Gets(operand);
        else if (name.equals("puti"))
            return new PutI(operand);
        else if (name.equals("putc"))
            return new PutC(operand);
        else if (name.equals("puts"))
            return new PutS(operand);
        else
            throw new IllegalArgumentException("Unknown instruction: " + name);
    }

    private static long parseOperand(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        return Long.parseLong(s);
    }
}
