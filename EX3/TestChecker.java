import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillLexer;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestChecker {
    public static void main(String[] args) {
        try {
            InputStream input = Files.newInputStream(Path.of(args[0]));
            CharStream charStream = CharStreams.fromStream(input);
            MandrillLexer lexer = new MandrillLexer(charStream);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            MandrillParser parser = new MandrillParser(tokens);
            MandrillParser.ProgramContext tree = parser.program();

            cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable table = new cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable();
            cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolCollector.collect(tree, table);

            try {
                new cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SemanticChecker(table).visit(tree);
                System.out.println("Pass");
            } catch (cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SemanticException e) {
                System.out.println("Error: " + e.getMessage() + " at line " + e.line + ", column " + e.column);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
