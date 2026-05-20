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

        switch (name) {
            case "nop":
                return new Nop(operand);
            case "dstore":
                return new DStore(operand);
            case "dload":
                return new DLoad(operand);
            case "dlload":
                return new DLLoad(operand);
            case "daload":
                return new DALoad(operand);
            case "dwrite":
                return new DWrite(operand);
            case "dlwrite":
                return new DLWrite(operand);
            case "dawrite":
                return new DAWrite(operand);
            case "eval":
                return new Eval(operand);
            case "jump":
                return new Jump(operand);
            case "jal":
                return new Jal(operand);
            case "ret":
                return new Ret(operand);
            case "malloc":
                return new Malloc(operand);
            case "geti":
                return new GetI(operand);
            case "getc":
                return new GetC(operand);
            case "gets":
                return new Gets(operand);
            case "puti":
                return new PutI(operand);
            case "putc":
                return new PutC(operand);
            case "puts":
                return new PutS(operand);
            default:
                throw new IllegalArgumentException("Unknown instruction: " + name);
        }
    }

    private static long parseOperand(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        return Long.parseLong(s);
    }
}
