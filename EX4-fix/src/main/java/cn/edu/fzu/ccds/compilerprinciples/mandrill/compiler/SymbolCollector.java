package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

public final class SymbolCollector extends MandrillBaseVisitor<Void> {

    private final SymbolTable table;
    private SymbolTable.FunctionSymbol currentFunction;

    private SymbolCollector(SymbolTable table) {
        this.table = table;
    }

    public static void collect(MandrillParser.ProgramContext programContext, SymbolTable table) {
        SymbolCollector collector = new SymbolCollector(table);
        collector.registerFunctions(programContext);
        collector.visitProgram(programContext);
        table.finalizeLayout();
    }

    private void registerFunctions(MandrillParser.ProgramContext programContext) {
        for (MandrillParser.FunctionDefContext functionContext : programContext.functionDef()) {
            String name = functionContext.Identifier().getText();
            SymbolTable.ValueKind returnKind = functionContext.arraySuffix() == null
                    ? SymbolTable.ValueKind.INT
                    : SymbolTable.ValueKind.ARRAY;
            SymbolTable.FunctionSymbol functionSymbol = table.defineFunction(name, returnKind, functionContext);

            List<MandrillParser.ParameterContext> parameters = functionContext.parameterList() == null
                    ? List.of()
                    : functionContext.parameterList().parameter();
            for (MandrillParser.ParameterContext parameterContext : parameters) {
                String parameterName = parameterContext.Identifier().getText();
                SymbolTable.ValueKind parameterKind = parameterContext.arraySuffix() == null
                        ? SymbolTable.ValueKind.INT
                        : SymbolTable.ValueKind.ARRAY;
                table.defineParameter(functionSymbol, parameterName, parameterKind);
            }
        }
    }

    @Override
    public Void visitProgram(MandrillParser.ProgramContext ctx) {
        for (int index = 0; index < ctx.getChildCount(); index++) {
            ParseTree child = ctx.getChild(index);
            if (child instanceof MandrillParser.FunctionDefContext) {
                MandrillParser.FunctionDefContext functionDefContext = (MandrillParser.FunctionDefContext) child;
                visitFunctionDef(functionDefContext);
            } else if (child instanceof MandrillParser.StatementContext) {
                MandrillParser.StatementContext statementContext = (MandrillParser.StatementContext) child;
                visitStatement(statementContext);
            }
        }
        return null;
    }

    @Override
    public Void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        currentFunction = table.resolveFunction(ctx.Identifier().getText());
        visit(ctx.stmtBlock());
        currentFunction = null;
        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.Identifier() != null && ctx.arraySuffix() != null) {
            resolveVariableUse(ctx.Identifier().getText(), true, null);
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx) {
        String name = ctx.Identifier().getText();
        SymbolTable.ValueKind kind = ctx.arraySuffix() == null
                ? SymbolTable.ValueKind.INT
                : SymbolTable.ValueKind.ARRAY;
        if (currentFunction == null || ctx.scope.getType() == MandrillParser.Global) {
            table.defineGlobal(name, kind);
        } else {
            table.defineLocal(currentFunction, name, kind);
        }
        return null;
    }

    @Override
    public Void visitTargetVariable(MandrillParser.TargetVariableContext ctx) {
        resolveVariableUse(ctx.Identifier().getText(), ctx.LeftBracket() != null, ctx.expression());
        return null;
    }

    @Override
    public Void visitSourceVariable(MandrillParser.SourceVariableContext ctx) {
        resolveVariableUse(ctx.Identifier().getText(), ctx.LeftBracket() != null, ctx.expression());
        return null;
    }

    @Override
    public Void visitFunctionCall(MandrillParser.FunctionCallContext ctx) {
        if (table.resolveFunction(ctx.Identifier().getText()) == null) {
            throw new IllegalStateException("Undefined function: " + ctx.Identifier().getText());
        }
        return visitChildren(ctx);
    }

    private void resolveVariableUse(String name, boolean indexed, MandrillParser.ExpressionContext indexExpression) {
        if (currentFunction != null) {
            if (currentFunction.getLocals().containsKey(name) || currentFunction.getParameters().containsKey(name)) {
                if (indexExpression != null) {
                    visit(indexExpression);
                }
                return;
            }
        }
        table.defineGlobal(name, indexed ? SymbolTable.ValueKind.ARRAY : SymbolTable.ValueKind.INT);
        if (indexExpression != null) {
            visit(indexExpression);
        }
    }
}