package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;

public class SymbolCollector extends MandrillBaseVisitor<Void> {
    private final SymbolTable symbolTable;

    public SymbolCollector(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    public static void collect(MandrillParser.ProgramContext tree, SymbolTable table) {
        SymbolCollector collector = new SymbolCollector(table);
        collector.visit(tree);
    }

    @Override
    public Void visitProgram(MandrillParser.ProgramContext ctx) {
        for (MandrillParser.FunctionDefContext funcCtx : ctx.functionDef()) {
            String funcName = funcCtx.Identifier().getText();
            boolean returnsArray = funcCtx.arraySuffix() != null;
            symbolTable.addFunction(funcName, returnsArray);
        }

        for (MandrillParser.FunctionDefContext funcCtx : ctx.functionDef()) {
            visitFunctionDef(funcCtx);
        }

        for (MandrillParser.StatementContext stmtCtx : ctx.statement()) {
            visitStatement(stmtCtx);
        }

        symbolTable.saveState();
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

        symbolTable.saveParamState();
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
            MandrillParser.DeclarationStmtContext declCtx = ctx.declarationStmt();
            String varName = declCtx.Identifier().getText();
            boolean isArray = declCtx.arraySuffix() != null;

            if (declCtx.scope != null && declCtx.scope.getText().equals("local")) {
                symbolTable.addLocalVariable(varName, isArray);
            } else if (declCtx.scope != null && declCtx.scope.getText().equals("global")) {
                symbolTable.addGlobalVariable(varName, isArray);
            } else {
                symbolTable.addGlobalVariable(varName, isArray);
            }
        } else if (ctx.assignStatement() != null) {
            visitAssignStatement(ctx.assignStatement());
        } else if (ctx.loopStatement() != null) {
            visitLoopStatement(ctx.loopStatement());
        } else if (ctx.conditionStatement() != null) {
            visitConditionStatement(ctx.conditionStatement());
        } else if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        }
        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            MandrillParser.LvalueContext lvalueCtx = ctx.lvalue();
            if (lvalueCtx instanceof MandrillParser.TargetVariableContext) {
                MandrillParser.TargetVariableContext targetCtx = (MandrillParser.TargetVariableContext) lvalueCtx;
                String varName = targetCtx.Identifier().getText();
                boolean isArray = targetCtx.expression() != null;
                SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
                if (info == null) {
                    symbolTable.addGlobalVariable(varName, isArray);
                } else if (isArray) {
                    // Late type binding: if used with subscript, mark as array
                    info.isArray = true;
                }
                if (targetCtx.expression() != null) {
                    visitExpression(targetCtx.expression());
                }
            }
        } else if (ctx.Identifier() != null) {
            String varName = ctx.Identifier().getText();
            boolean isArray = ctx.arraySuffix() != null;
            if (symbolTable.lookup(varName) == null) {
                symbolTable.addGlobalVariable(varName, isArray);
            }
        }
        if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
            visitExpression(ctx.rvalue().expression());
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

    private Void visitExpression(MandrillParser.ExpressionContext ctx) {
        if (ctx instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext sourceVarCtx = (MandrillParser.SourceVariableContext) ctx;
            String varName = sourceVarCtx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
            if (info == null) {
                symbolTable.addGlobalVariable(varName, false);
            }
            // If used with subscript, mark as array (late type binding)
            if (sourceVarCtx.expression() != null) {
                if (info != null) {
                    info.isArray = true;
                }
                visitExpression(sourceVarCtx.expression());
            }
        } else if (ctx instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext funcCallCtx = (MandrillParser.FunctionCallContext) ctx;
            if (funcCallCtx.argumentList() != null) {
                for (MandrillParser.ExpressionContext argExpr : funcCallCtx.argumentList().expression()) {
                    visitExpression(argExpr);
                }
            }
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
}
