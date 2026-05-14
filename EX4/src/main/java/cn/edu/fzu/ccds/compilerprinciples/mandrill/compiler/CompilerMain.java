package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompilerMain {

    public static void main(String[] args) {
        String inputPath = null;
        String outputPath = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (inputPath == null) {
                inputPath = arg;
            } else if (outputPath == null) {
                outputPath = arg;
            } else {
                System.err.println("Usage: CompilerMain <input.mds> [output.asm]");
                System.exit(1);
            }
        }

        if (inputPath == null) {
            System.err.println("Usage: CompilerMain <input.mds> [output.asm]");
            System.exit(1);
        }

        try {
            String assembly = compileFile(inputPath);
            if (outputPath != null) {
                Files.writeString(Path.of(outputPath), assembly, StandardCharsets.UTF_8);
            } else {
                System.out.print(assembly);
            }
        } catch (Exception e) {
            System.err.println("Compilation error: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    public static String compileFile(String path) throws IOException {
        try (InputStream in = new FileInputStream(path)) {
            Compiler backend = new CompilerImpl();
            return backend.compile(in);
        }
    }
}
