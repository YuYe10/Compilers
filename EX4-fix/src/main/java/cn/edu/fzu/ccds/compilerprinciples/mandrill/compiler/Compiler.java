package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillLexer;import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.io.InputStream;

public interface Compiler {
    String compile(InputStream inputStream) throws IOException;

    static CompileContext frontend(InputStream inputStream) throws IOException {
        CharStream charStream = CharStreams.fromStream(inputStream);
        MandrillLexer lexer = new MandrillLexer(charStream);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MandrillParser parser = new MandrillParser(tokens);
        MandrillParser.ProgramContext tree = parser.program();

        SymbolTable table = new SymbolTable();
        SymbolCollector.collect(tree, table);
        new SemanticChecker(table).visit(tree);

        return new CompileContext(tree, table);
    }

    class CompileContext {
        private final MandrillParser.ProgramContext tree;
        private final SymbolTable table;

        public CompileContext(MandrillParser.ProgramContext tree, SymbolTable table) {
            this.tree = tree;
            this.table = table;
        }

        public MandrillParser.ProgramContext tree() {
            return tree;
        }

        public SymbolTable table() {
            return table;
        }
    }
}