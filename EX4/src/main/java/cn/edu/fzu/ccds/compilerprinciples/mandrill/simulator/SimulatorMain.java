package cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.assembler.AssemblyParser;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.instruction.Instruction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Mandrill v2.0 虚拟机汇编模拟器 CLI 入口。
 *
 * 用法：
 * SimulatorMain <file>
 * SimulatorMain <file> <in>
 * SimulatorMain <file> <in> <out>
 *
 * 其中 "-" 表示 stdin（输入）或 stdout（输出）。
 */
public class SimulatorMain {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println("用法: SimulatorMain <file> [<in>] [<out>]");
            System.err.println("  <file>  : 汇编文件路径");
            System.err.println("  <in>    : 输入文件路径（省略或 '-' 表示 stdin）");
            System.err.println("  <out>   : 输出文件路径（省略或 '-' 表示 stdout）");
            System.exit(1);
        }

        String file = args[0];
        String in = args.length >= 2 ? args[1] : "-";
        String out = args.length >= 3 ? args[2] : "-";

        InputStream inputStream;
        PrintStream printStream;

        try {
            inputStream = "-".equals(in) ? System.in : new FileInputStream(in);
            printStream = "-".equals(out) ? System.out : new PrintStream(out);
        } catch (FileNotFoundException e) {
            System.err.println("文件未找到: " + e.getMessage());
            System.exit(1);
            return;
        }

        try (FileInputStream assemblyFileStream = new FileInputStream(file);
                inputStream;
                printStream) {
            List<Instruction> instructions = AssemblyParser.parse(assemblyFileStream);

            SimulatorMemory memory = new SimulatorMemory(instructions, inputStream, printStream);
            while (memory.getProgramCounter() != 0xFFFFFFFFL) {
                int index = (int) (memory.getProgramCounter() / 8);
                if (index < 0 || index >= instructions.size()) {
                    throw new IllegalStateException("Program counter out of bounds: " + memory.getProgramCounter());
                }
                Instruction instruction = instructions.get(index);
                instruction.execute(memory);
            }
        } catch (Exception e) {
            System.err.println("=== Assembly file content ===");
            try {
                System.err.print(Files.readString(Path.of(file), StandardCharsets.UTF_8));
            } catch (IOException ioException) {
                System.err.println("Could not read assembly file: " + ioException.getMessage());
            }
            System.err.println("============================");
            System.err.println("Runtime error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
