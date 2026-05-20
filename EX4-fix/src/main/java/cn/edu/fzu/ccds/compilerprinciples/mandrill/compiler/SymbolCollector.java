package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class SymbolCollector extends MandrillBaseVisitor<Void> {

    private final SymbolTable table;
    private SymbolTable.FunctionSymbol currentFunction;
    private final Deque<Map<String, SymbolTable.VariableSymbol>> scopeStack = new ArrayDeque<>();

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
        pushScope();
        for (SymbolTable.VariableSymbol parameterSymbol : currentFunction.getParameterOrder()) {
            currentScope().put(parameterSymbol.getName(), parameterSymbol);
        }
        visit(ctx.stmtBlock());
        popScope();
        currentFunction = null;
        return null;
    }

    @Override
    public Void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        if (currentFunction == null) {
            return visitChildren(ctx);
        }
        pushScope();
        visitChildren(ctx);
        popScope();
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
            if (currentScope().containsKey(name)) {
                throw new IllegalStateException(
                        "Duplicate local: " + name + " in function " + currentFunction.getName());
            }
            SymbolTable.VariableSymbol symbol = table.defineLocal(currentFunction, name, kind);
            currentScope().put(name, symbol);
            table.registerDeclaredVariable(currentFunction, ctx, symbol);
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
        if (currentFunction != null && resolveVisible(name) != null) {
            if (indexExpression != null) {
                visit(indexExpression);
            }
            return;
        }
        table.defineGlobal(name, indexed ? SymbolTable.ValueKind.ARRAY : SymbolTable.ValueKind.INT);
        if (indexExpression != null) {
            visit(indexExpression);
        }
    }

    private SymbolTable.VariableSymbol resolveVisible(String name) {
        for (Map<String, SymbolTable.VariableSymbol> scope : scopeStack) {
            SymbolTable.VariableSymbol symbol = scope.get(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    private void pushScope() {
        scopeStack.push(new LinkedHashMap<>());
    }

    private void popScope() {
        if (scopeStack.isEmpty()) {
            throw new IllegalStateException("Scope stack underflow");
        }
        scopeStack.pop();
    }

    private Map<String, SymbolTable.VariableSymbol> currentScope() {
        if (scopeStack.isEmpty()) {
            throw new IllegalStateException("No active scope");
        }
        return scopeStack.peek();
    }
}