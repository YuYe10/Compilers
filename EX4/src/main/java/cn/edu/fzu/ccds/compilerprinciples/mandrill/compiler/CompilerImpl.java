package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillLexer;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;

public class CompilerImpl implements Compiler {

    @Override
    public String compile(InputStream inputStream) throws IOException {
        CharStream charStream = CharStreams.fromStream(inputStream);
        MandrillLexer lexer = new MandrillLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MandrillParser parser = new MandrillParser(tokens);
        MandrillParser.ProgramContext tree = parser.program();

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
}
