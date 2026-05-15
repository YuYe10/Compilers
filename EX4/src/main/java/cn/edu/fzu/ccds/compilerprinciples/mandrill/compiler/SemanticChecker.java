package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;

public class SemanticChecker extends MandrillBaseVisitor<Void> {
    private final SymbolTable symbolTable;

    public SemanticChecker(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    @Override
    public Void visitProgram(MandrillParser.ProgramContext ctx) {
        for (MandrillParser.FunctionDefContext funcCtx : ctx.functionDef()) {
            visitFunctionDef(funcCtx);
        }
        for (MandrillParser.StatementContext stmtCtx : ctx.statement()) {
            visitStatement(stmtCtx);
        }
        return null;
    }

    @Override
    public Void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        symbolTable.enterScope();

        if (ctx.parameterList() != null) {
            for (MandrillParser.ParameterContext paramCtx : ctx.parameterList().parameter()) {
                String paramName = paramCtx.Identifier().getText();
                boolean isArray = paramCtx.arraySuffix() != null;
                symbolTable.addParameter(paramName, isArray);
            }
        }

        if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        }

        symbolTable.exitScope();
        return null;
    }

    @Override
    public Void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        for (MandrillParser.StatementContext stmtCtx : ctx.statement()) {
            visitStatement(stmtCtx);
        }
        return null;
    }

    @Override
    public Void visitStatement(MandrillParser.StatementContext ctx) {
        if (ctx.declarationStmt() != null) {
            visitDeclarationStmt(ctx.declarationStmt());
        } else if (ctx.assignStatement() != null) {
            visitAssignStatement(ctx.assignStatement());
        } else if (ctx.loopStatement() != null) {
            visitLoopStatement(ctx.loopStatement());
        } else if (ctx.conditionStatement() != null) {
            visitConditionStatement(ctx.conditionStatement());
        } else if (ctx.jumpStmt() != null) {
            visitJumpStmt(ctx.jumpStmt());
        } else if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        }
        return null;
    }

    @Override
    public Void visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx) {
        String varName = ctx.Identifier().getText();
        SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
        if (info == null) {
            boolean isArray = ctx.arraySuffix() != null;
            if (ctx.scope != null && ctx.scope.getText().equals("local")) {
                symbolTable.addLocalVariable(varName, isArray);
            } else {
                symbolTable.addGlobalVariable(varName, isArray);
            }
        }
        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            visitLvalue(ctx.lvalue());
        }
        if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
            visitExpression(ctx.rvalue().expression());
        }
        return null;
    }

    private Void visitLvalue(MandrillParser.LvalueContext ctx) {
        if (ctx instanceof MandrillParser.TargetVariableContext) {
            MandrillParser.TargetVariableContext targetCtx = (MandrillParser.TargetVariableContext) ctx;
            String varName = targetCtx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
            if (info == null) {
                throw new SemanticError("Undefined variable: " + varName);
            }
            if (targetCtx.expression() != null && !info.isArray) {
                throw new SemanticError("Variable '" + varName + "' is not an array");
            }
            if (targetCtx.expression() != null) {
                visitExpression(targetCtx.expression());
            }
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(MandrillParser.LoopStatementContext ctx) {
        if (ctx.expr != null) {
            visitExpression(ctx.expr);
        }
        if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        }
        return null;
    }

    @Override
    public Void visitConditionStatement(MandrillParser.ConditionStatementContext ctx) {
        if (ctx.expr != null) {
            visitExpression(ctx.expr);
        }
        if (ctx.thenStatement != null) {
            visitStmtBlock(ctx.thenStatement);
        }
        if (ctx.elseStatement != null) {
            visitStmtBlock(ctx.elseStatement);
        }
        return null;
    }

    @Override
    public Void visitJumpStmt(MandrillParser.JumpStmtContext ctx) {
        if (ctx.expression() != null) {
            visitExpression(ctx.expression());
        }
        return null;
    }

    private Void visitExpression(MandrillParser.ExpressionContext ctx) {
        if (ctx instanceof MandrillParser.IntLiteralContext) {
            return null;
        } else if (ctx instanceof MandrillParser.CharLiteralContext) {
            return null;
        } else if (ctx instanceof MandrillParser.StringLiteralContext) {
            return null;
        } else if (ctx instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext sourceVarCtx = (MandrillParser.SourceVariableContext) ctx;
            String varName = sourceVarCtx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
            if (info == null) {
                throw new SemanticError("Undefined variable: " + varName);
            }
            if (sourceVarCtx.expression() != null && !info.isArray) {
                throw new SemanticError("Variable '" + varName + "' is not an array");
            }
            if (sourceVarCtx.expression() != null) {
                visitExpression(sourceVarCtx.expression());
            }
        } else if (ctx instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext funcCallCtx = (MandrillParser.FunctionCallContext) ctx;
            String funcName = funcCallCtx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookupGlobal(funcName);
            if (info == null) {
                throw new SemanticError("Undefined function: " + funcName);
            }
            if (info.kind != SymbolTable.SymbolKind.FUNCTION) {
                throw new SemanticError("'" + funcName + "' is not a function");
            }
            if (funcCallCtx.argumentList() != null) {
                for (MandrillParser.ExpressionContext argExpr : funcCallCtx.argumentList().expression()) {
                    visitExpression(argExpr);
                }
            }
        } else if (ctx instanceof MandrillParser.InputIntContext) {
            return null;
        } else if (ctx instanceof MandrillParser.InputChatContext) {
            return null;
        } else if (ctx instanceof MandrillParser.MulDivModExpressionContext) {
            MandrillParser.MulDivModExpressionContext mulDivCtx = (MandrillParser.MulDivModExpressionContext) ctx;
            if (mulDivCtx.expression(0) != null)
                visitExpression(mulDivCtx.expression(0));
            if (mulDivCtx.expression(1) != null)
                visitExpression(mulDivCtx.expression(1));
        } else if (ctx instanceof MandrillParser.AddSubExpressionContext) {
            MandrillParser.AddSubExpressionContext addSubCtx = (MandrillParser.AddSubExpressionContext) ctx;
            if (addSubCtx.expression(0) != null)
                visitExpression(addSubCtx.expression(0));
            if (addSubCtx.expression(1) != null)
                visitExpression(addSubCtx.expression(1));
        } else if (ctx instanceof MandrillParser.ComparingExpressionContext) {
            MandrillParser.ComparingExpressionContext compareCtx = (MandrillParser.ComparingExpressionContext) ctx;
            if (compareCtx.expression(0) != null)
                visitExpression(compareCtx.expression(0));
            if (compareCtx.expression(1) != null)
                visitExpression(compareCtx.expression(1));
        } else if (ctx instanceof MandrillParser.EqualityExpressionContext) {
            MandrillParser.EqualityExpressionContext eqCtx = (MandrillParser.EqualityExpressionContext) ctx;
            if (eqCtx.expression(0) != null)
                visitExpression(eqCtx.expression(0));
            if (eqCtx.expression(1) != null)
                visitExpression(eqCtx.expression(1));
        } else if (ctx instanceof MandrillParser.SubgroupExpressionContext) {
            MandrillParser.SubgroupExpressionContext subExprCtx = (MandrillParser.SubgroupExpressionContext) ctx;
            if (subExprCtx.expression() != null) {
                visitExpression(subExprCtx.expression());
            }
        }
        return null;
    }

    public static class SemanticError extends RuntimeException {
        public SemanticError(String message) {
            super(message);
        }
    }
}
