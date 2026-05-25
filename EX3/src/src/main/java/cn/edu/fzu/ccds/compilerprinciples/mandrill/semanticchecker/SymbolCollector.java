package cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import static cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable.MandrillType;
import static cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable.ScopeType;

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
    public Void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        String funcName = ctx.Identifier().getText();
        boolean isArrayFunc = ctx.arraySuffix() != null;

        symbolTable.declareSymbol(funcName,
                isArrayFunc ? MandrillType.ARRAY_PTR : MandrillType.INT,
                ScopeType.GLOBAL);

        symbolTable.enterFunction();

        MandrillParser.ParameterListContext paramList = ctx.parameterList();
        if (paramList != null) {
            for (MandrillParser.ParameterContext param : paramList.parameter()) {
                String paramName = param.Identifier().getText();
                boolean isArrayParam = param.arraySuffix() != null;
                symbolTable.declareSymbol(paramName,
                        isArrayParam ? MandrillType.ARRAY_PTR : MandrillType.INT,
                        ScopeType.LOCAL);
            }
        }

        visit(ctx.stmtBlock());

        symbolTable.exitFunction();
        return null;
    }

    @Override
    public Void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        symbolTable.enterBlock();
        super.visitStmtBlock(ctx);
        symbolTable.exitBlock();
        return null;
    }

    @Override
    public Void visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx) {
        String varName = ctx.Identifier().getText();
        boolean isArray = ctx.arraySuffix() != null;
        ScopeType scopeType = ctx.Global() != null ? ScopeType.GLOBAL : ScopeType.LOCAL;

        symbolTable.declareSymbol(varName,
                isArray ? MandrillType.ARRAY_PTR : MandrillType.UNKNOWN,
                scopeType);
        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            MandrillParser.LvalueContext lvalue = ctx.lvalue();
            if (lvalue instanceof MandrillParser.TargetVariableContext) {
                MandrillParser.TargetVariableContext target = (MandrillParser.TargetVariableContext) lvalue;
                String varName = target.Identifier().getText();
                boolean isArrayAccess = target.LeftBracket() != null;

                if (!symbolTable.isDeclared(varName)) {
                    if (isArrayAccess) {
                        symbolTable.declareSymbol(varName, MandrillType.ARRAY_PTR, ScopeType.GLOBAL);
                    }
                }

                MandrillParser.RvalueContext rvalue = ctx.rvalue();
                if (rvalue != null) {
                    MandrillType exprType = inferExpressionType(rvalue.expression());
                    if (exprType != MandrillType.UNKNOWN && !symbolTable.isDeclared(varName)) {
                        symbolTable.declareSymbol(varName, exprType, ScopeType.GLOBAL);
                    } else if (symbolTable.isDeclared(varName) && !isArrayAccess) {
                        symbolTable.updateSymbolType(varName, exprType);
                    }
                }
            }
        } else if (ctx.Identifier() != null && ctx.arraySuffix() != null) {
            String varName = ctx.Identifier().getText();
            if (!symbolTable.isDeclared(varName)) {
                symbolTable.declareSymbol(varName, MandrillType.ARRAY_PTR, ScopeType.GLOBAL);
            }
        }

        return super.visitAssignStatement(ctx);
    }

    private MandrillType inferExpressionType(MandrillParser.ExpressionContext expr) {
        if (expr instanceof MandrillParser.IntLiteralContext) {
            return MandrillType.INT;
        } else if (expr instanceof MandrillParser.CharLiteralContext) {
            return MandrillType.INT;
        } else if (expr instanceof MandrillParser.StringLiteralContext) {
            return MandrillType.ARRAY_PTR;
        } else if (expr instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext varCtx = (MandrillParser.SourceVariableContext) expr;
            String varName = varCtx.Identifier().getText();
            return symbolTable.getType(varName);
        } else if (expr instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext funcCall = (MandrillParser.FunctionCallContext) expr;
            String funcName = funcCall.Identifier().getText();
            return symbolTable.getType(funcName);
        } else if (expr instanceof MandrillParser.InputIntContext || expr instanceof MandrillParser.InputChatContext) {
            return MandrillType.INT;
        } else if (expr instanceof MandrillParser.SubgroupExpressionContext) {
            MandrillParser.SubgroupExpressionContext sub = (MandrillParser.SubgroupExpressionContext) expr;
            return inferExpressionType(sub.expression());
        } else if (expr instanceof MandrillParser.MulDivModExpressionContext ||
                expr instanceof MandrillParser.AddSubExpressionContext ||
                expr instanceof MandrillParser.ComparingExpressionContext ||
                expr instanceof MandrillParser.EqualityExpressionContext) {
            return MandrillType.INT;
        }
        return MandrillType.UNKNOWN;
    }
}