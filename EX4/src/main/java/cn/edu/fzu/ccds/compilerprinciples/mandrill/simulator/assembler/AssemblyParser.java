package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.assembler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssemblyParser {

    public static List<Instruction> parse(InputStream inputStream) throws IOException {
        List<String> lines = new ArrayList<>();
        Map<String, Integer> labelAddresses = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            int instructionIndex = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                    continue;
                }
                if (line.startsWith("label_")) {
                    String labelName = line.substring(6);
                    labelAddresses.put(labelName, instructionIndex);
                } else {
                    lines.add(line);
                    instructionIndex++;
                }
            }
        }
        
        List<Instruction> instructions = new ArrayList<>();
        for (String line : lines) {
            Instruction instruction = parseLine(line, labelAddresses);
            instructions.add(instruction);
        }
        
        return instructions;
    }

    private static Instruction parseLine(String line, Map<String, Integer> labelAddresses) {
        String[] parts = line.split("\\s+");
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty instruction line");
        }
        String name = parts[0].toLowerCase();
        long operand = 0;
        if (parts.length >= 2) {
            operand = parseOperand(parts[1], labelAddresses);
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
            case "pop":
                return new Pop(operand);
            case "dconst":
                return new DConst(operand);
            case "dconst_label":
                return new DConst(operand);
            case "setsp":
                return new SetSP(operand);
            case "swap":
                return new Swap(operand);
            default:
                throw new IllegalArgumentException("Unknown instruction: " + name);
        }
    }

    private static long parseOperand(String s, Map<String, Integer> labelAddresses) {
        s = s.trim();
        if (labelAddresses.containsKey(s)) {
            return labelAddresses.get(s);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return Long.parseLong(s.substring(2), 16);
        }
        return Long.parseLong(s);
    }
}
