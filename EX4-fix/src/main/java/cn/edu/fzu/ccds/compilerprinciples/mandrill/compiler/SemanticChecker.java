package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;

public class SemanticChecker extends MandrillBaseVisitor<Void> {
    private final SymbolTable table;

    public SemanticChecker(SymbolTable table) {
        this.table = table;
    }

    @Override
    public Void visitTargetVariable(MandrillParser.TargetVariableContext ctx) {
        String name = ctx.Identifier().getText();
        Symbol symbol = table.lookup(name);
        if (symbol == null) {
            VariableSymbol var = new VariableSymbol(name, Symbol.Scope.GLOBAL, ctx.expression() != null);
            table.addGlobal(var);
        } else if (ctx.expression() != null && !symbol.isArray()) {
            throw new SymbolTable.SemanticError("Variable " + name + " is not an array");
        }
        return null;
    }

    @Override
    public Void visitSourceVariable(MandrillParser.SourceVariableContext ctx) {
        String name = ctx.Identifier().getText();
        Symbol symbol = table.lookup(name);
        if (symbol == null) {
            VariableSymbol var = new VariableSymbol(name, Symbol.Scope.GLOBAL, ctx.expression() != null);
            table.addGlobal(var);
        } else if (ctx.expression() != null && !symbol.isArray()) {
            throw new SymbolTable.SemanticError("Variable " + name + " is not an array");
        }
        return null;
    }

    @Override
    public Void visitFunctionCall(MandrillParser.FunctionCallContext ctx) {
        String funcName = ctx.Identifier().getText();
        FunctionSymbol func = table.getFunction(funcName);

        if (func == null) {
            throw new SymbolTable.SemanticError("Undefined function: " + funcName);
        }

        int argCount = ctx.argumentList() != null ? ctx.argumentList().expression().size() : 0;
        int paramCount = func.getParamCount();

        if (argCount != paramCount) {
            throw new SymbolTable.SemanticError("Function " + funcName + " expects " + paramCount +
                    " arguments, but got " + argCount);
        }

        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            ctx.lvalue().accept(this);
        }
        if (ctx.rvalue() != null) {
            ctx.rvalue().accept(this);
        }
        if (ctx.Identifier() != null) {
            String name = ctx.Identifier().getText();
            Symbol symbol = table.lookup(name);
            if (symbol == null) {
                VariableSymbol var = new VariableSymbol(name, Symbol.Scope.GLOBAL, ctx.arraySuffix() != null);
                table.addGlobal(var);
            } else if (ctx.arraySuffix() != null && !symbol.isArray()) {
                throw new SymbolTable.SemanticError("Variable " + name + " is not an array");
            }
            if (ctx.rvalue() != null) {
                ctx.rvalue().accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visitJumpStmt(MandrillParser.JumpStmtContext ctx) {
        if (ctx.expression() != null) {
            ctx.expression().accept(this);
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(MandrillParser.LoopStatementContext ctx) {
        ctx.expr.accept(this);
        ctx.stmtBlock().accept(this);
        return null;
    }

    @Override
    public Void visitConditionStatement(MandrillParser.ConditionStatementContext ctx) {
        ctx.expr.accept(this);
        ctx.thenStatement.accept(this);
        if (ctx.elseStatement != null) {
            ctx.elseStatement.accept(this);
        }
        return null;
    }

    @Override
    public Void visitMulDivModExpression(MandrillParser.MulDivModExpressionContext ctx) {
        ctx.expression(0).accept(this);
        ctx.expression(1).accept(this);
        return null;
    }

    @Override
    public Void visitAddSubExpression(MandrillParser.AddSubExpressionContext ctx) {
        ctx.expression(0).accept(this);
        ctx.expression(1).accept(this);
        return null;
    }

    @Override
    public Void visitComparingExpression(MandrillParser.ComparingExpressionContext ctx) {
        ctx.expression(0).accept(this);
        ctx.expression(1).accept(this);
        return null;
    }

    @Override
    public Void visitEqualityExpression(MandrillParser.EqualityExpressionContext ctx) {
        ctx.expression(0).accept(this);
        ctx.expression(1).accept(this);
        return null;
    }

    @Override
    public Void visitSubgroupExpression(MandrillParser.SubgroupExpressionContext ctx) {
        ctx.expression().accept(this);
        return null;
    }
}