package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;

public final class SemanticChecker extends MandrillBaseVisitor<Void> {

    private final SymbolTable table;

    public SemanticChecker(SymbolTable table) {
        this.table = table;
    }

    @Override
    public Void visitFunctionCall(MandrillParser.FunctionCallContext ctx) {
        if (table.resolveFunction(ctx.Identifier().getText()) == null) {
            throw new IllegalStateException("Undefined function: " + ctx.Identifier().getText());
        }
        return visitChildren(ctx);
    }
}