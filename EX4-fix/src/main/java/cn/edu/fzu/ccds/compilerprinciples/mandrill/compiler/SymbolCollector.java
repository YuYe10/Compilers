package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;

public class SymbolCollector extends MandrillBaseVisitor<Void> {
    private final SymbolTable table;

    public SymbolCollector(SymbolTable table) {
        this.table = table;
    }

    public static void collect(MandrillParser.ProgramContext tree, SymbolTable table) {
        SymbolCollector collector = new SymbolCollector(table);
        collector.visit(tree);
    }

    @Override
    public Void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        String funcName = ctx.Identifier().getText();
        boolean isArrayReturn = ctx.arraySuffix() != null;
        
        FunctionSymbol func = new FunctionSymbol(funcName, isArrayReturn);
        table.addGlobal(func);
        table.setCurrentFunction(func);
        table.enterScope();

        if (ctx.parameterList() != null) {
            visitParameterList(ctx.parameterList());
        }

        visitStmtBlock(ctx.stmtBlock());

        table.exitScope();
        table.setCurrentFunction(null);
        return null;
    }

    @Override
    public Void visitParameterList(MandrillParser.ParameterListContext ctx) {
        for (int i = 0; i < ctx.Identifier().size(); i++) {
            String name = ctx.Identifier().get(i).getText();
            boolean isArray = ctx.arraySuffix(i) != null;
            VariableSymbol param = new VariableSymbol(name, Symbol.Scope.PARAMETER, isArray);
            table.addParameter(param);
        }
        return null;
    }

    @Override
    public Void visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx) {
        String name = ctx.Identifier().getText();
        boolean isArray = ctx.arraySuffix() != null;
        Symbol.Scope scope = ctx.scope.getText().equals("global") ? Symbol.Scope.GLOBAL : Symbol.Scope.LOCAL;

        if (scope == Symbol.Scope.GLOBAL) {
            VariableSymbol var = new VariableSymbol(name, scope, isArray);
            table.addGlobal(var);
        } else {
            VariableSymbol var = new VariableSymbol(name, scope, isArray);
            table.addLocal(var);
        }
        return null;
    }

    @Override
    public Void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        return super.visitStmtBlock(ctx);
    }
}