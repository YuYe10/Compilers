package cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义检查器。
 * 在 SymbolCollector 收集完符号表后运行，遍历 AST 进行类型检查。
 */
public class SemanticChecker extends MandrillBaseVisitor<MandrillType> {
    private void error(ParserRuleContext ctx, String message) {
        int line = ctx.getStart().getLine();
        int col = ctx.getStart().getCharPositionInLine();
        throw new SemanticException(line, col, message);
    }
}
