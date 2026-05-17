package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillLexer;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public class CompilerImpl implements Compiler {

    private String lastInput;

    @Override
    public String compile(InputStream inputStream) throws IOException {
        // 保存输入内容
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        lastInput = buffer.toString(StandardCharsets.UTF_8);

        // 重新创建输入流用于解析
        CharStream charStream = CharStreams.fromString(lastInput);
        MandrillLexer lexer = new MandrillLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MandrillParser parser = new MandrillParser(tokens);

        // 捕获语法错误
        ByteArrayOutputStream errorBuffer = new ByteArrayOutputStream();
        PrintStream errorStream = new PrintStream(errorBuffer);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        ConsoleErrorListener errorListener = new ConsoleErrorListener() {
            @Override
            public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
                                   Object offendingSymbol,
                                   int line,
                                   int charPositionInLine,
                                   String msg,
                                   org.antlr.v4.runtime.RecognitionException e) {
                errorStream.println("Syntax error at line " + line + ":" + charPositionInLine + ": " + msg);
            }
        };
        lexer.addErrorListener(errorListener);
        parser.addErrorListener(errorListener);

        MandrillParser.ProgramContext tree = parser.program();

        // 检查是否有语法错误
        String errorOutput = errorBuffer.toString();
        if (!errorOutput.isEmpty()) {
            throw new IOException(errorOutput);
        }

        SymbolTable table = new SymbolTable();
        SymbolCollector.collect(tree, table);

        table.reinitializeForSemanticCheck();

        try {
            new SemanticChecker(table).visit(tree);
        } catch (SemanticChecker.SemanticError e) {
            throw new IOException("Semantic error: " + e.getMessage());
        }

        // 重新收集符号，用于代码生成
        SymbolTable codeGenTable = new SymbolTable();
        SymbolCollector.collect(tree, codeGenTable);
        CodeGenerator codeGenerator = new CodeGenerator(codeGenTable);
        return codeGenerator.generate(tree);
    }

    public String getLastInput() {
        return lastInput;
    }
}
