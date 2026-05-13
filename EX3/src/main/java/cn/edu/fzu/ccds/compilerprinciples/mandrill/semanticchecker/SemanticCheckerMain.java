package cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillLexer;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 语义检查器 CLI 入口。
 *
 * 用法: SemanticCheckerMain [input.mds]
 * - 无参数时从标准输入读取 Mandrill 源程序
 * - 有参数时从第一个参数指定的文件读取
 * - 输出 Pass 或 Error 到标准输出
 */
public class SemanticCheckerMain {

    public static void main(String[] args) {
        try {
            InputStream input;
            if (args.length == 0) {
                input = System.in;
            } else {
                input = Files.newInputStream(Path.of(args[0]));
            }
            String result = check(input);
            System.out.println(result);
            if (input != System.in) {
                input.close();
            }
        } catch (Exception e) {
            System.out.println("Error");
        }
    }

    public static String check(InputStream inputStream) throws IOException {
        // 读取源代码内容以便在错误时显示
        byte[] bytes = inputStream.readAllBytes();
        String sourceCode = new String(bytes);
        List<String> lines = java.util.Arrays.asList(sourceCode.split("\\n"));

        try {
            // 使用读取的内容创建字符流
            CharStream charStream = CharStreams.fromString(sourceCode);
            MandrillLexer lexer = new MandrillLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            MandrillParser parser = new MandrillParser(tokens);
            MandrillParser.ProgramContext tree = parser.program();

            SymbolTable table = new SymbolTable();
            SymbolCollector.collect(tree, table);
            new SemanticChecker(table).visit(tree);
            return "Pass";
        } catch (SemanticException e) {
            // System.err.println(e.getMessage());
            // 输出错误位置的源代码内容
            /*
             * if (e.line > 0 && e.line <= lines.size()) {
             * System.err.println("\nSource code at error location:");
             * // 显示错误行及其前后各1行
             * int startLine = Math.max(0, e.line - 2);
             * int endLine = Math.min(lines.size() - 1, e.line);
             * for (int i = startLine; i <= endLine; i++) {
             * String lineNum = String.format("%3d:", i + 1);
             * System.err.println(lineNum + " " + lines.get(i));
             * // 在错误列位置显示箭头
             * if (i == e.line - 1) {
             * String arrow = " ".repeat(e.column + 4) + "^";
             * System.err.println(arrow);
             * }
             * }
             * }
             */
            return "Error";
        }
    }
}
