package cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

import static cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable.MandrillType;

public class SemanticChecker extends MandrillBaseVisitor<MandrillType> {
    private final SymbolTable symbolTable;
    private int loopNestingLevel;
    private int functionNestingLevel;

    public SemanticChecker(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.loopNestingLevel = 0;
        this.functionNestingLevel = 0;
    }

    private void error(ParserRuleContext ctx, String message) {
        int line = ctx.getStart().getLine();
        int col = ctx.getStart().getCharPositionInLine();
        throw new SemanticException(line, col, message);
    }

    @Override
    public MandrillType visitProgram(MandrillParser.ProgramContext ctx) {
        return super.visitProgram(ctx);
    }

    @Override
    public MandrillType visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        symbolTable.enterFunction();
        functionNestingLevel++;

        MandrillParser.ParameterListContext paramList = ctx.parameterList();
        if (paramList != null) {
            for (MandrillParser.ParameterContext param : paramList.parameter()) {
                String paramName = param.Identifier().getText();
                boolean isArrayParam = param.arraySuffix() != null;
            }
        }

        visit(ctx.stmtBlock());

        symbolTable.exitFunction();
        functionNestingLevel--;
        return MandrillType.UNKNOWN;
    }

    @Override
    public MandrillType visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        symbolTable.enterBlock();
        super.visitStmtBlock(ctx);
        symbolTable.exitBlock();
        return MandrillType.UNKNOWN;
    }

    @Override
    public MandrillType visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx) {
        String varName = ctx.Identifier().getText();
        boolean isGlobal = ctx.Global() != null;

        if (isGlobal) {
            // 全局声明：允许引用已存在的全局变量或声明新的全局变量
            // 检查是否在当前函数作用域内已声明同名局部变量
            SymbolTable.Symbol existing = symbolTable.lookup(varName);
            if (existing != null && existing.scopeLevel > 0 && existing.scope == SymbolTable.ScopeType.LOCAL) {
                error(ctx, "Variable '" + varName + "' is already declared as a local variable in this function");
            }
        } else {
            // 局部声明：检查是否已在当前作用域中声明
            SymbolTable.Symbol existing = symbolTable.lookup(varName);
            if (existing != null && existing.scopeLevel == symbolTable.getScopeLevel()) {
                error(ctx, "Variable '" + varName + "' is already declared in this scope");
            }
        }

        return super.visitDeclarationStmt(ctx);
    }

    @Override
    public MandrillType visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            MandrillParser.LvalueContext lvalue = ctx.lvalue();
            MandrillType leftType = visit(lvalue);

            if (leftType != null) {
                MandrillParser.RvalueContext rvalue = ctx.rvalue();
                if (rvalue != null) {
                    MandrillType rightType = visit(rvalue.expression());

                    if (lvalue instanceof MandrillParser.PrintIntegerContext) {

                    } else if (lvalue instanceof MandrillParser.PrintCharContext) {

                    } else if (lvalue instanceof MandrillParser.TargetVariableContext) {
                        MandrillParser.TargetVariableContext target = (MandrillParser.TargetVariableContext) lvalue;
                        String varName = target.Identifier().getText();
                        boolean isArrayAccess = target.LeftBracket() != null;

                        if (isArrayAccess) {
                            if (leftType != MandrillType.ARRAY_PTR) {
                                error(ctx, "Array index access requires an array variable");
                            }
                            if (rightType != MandrillType.INT) {
                                error(ctx, "Cannot assign non-integer value to array element");
                            }
                        } else {
                            if (leftType != MandrillType.UNKNOWN && leftType == MandrillType.ARRAY_PTR
                                    && rightType == MandrillType.INT) {
                                error(ctx, "Cannot assign integer value to array variable");
                            }
                        }
                    }
                }
            }
        } else if (ctx.Identifier() != null && ctx.arraySuffix() != null) {
            String varName = ctx.Identifier().getText();
            MandrillType varType = symbolTable.getType(varName);

            if (varType != MandrillType.UNKNOWN && varType != MandrillType.ARRAY_PTR) {
                error(ctx, "Cannot use array assignment on non-array variable");
            }
        }

        return MandrillType.UNKNOWN;
    }

    @Override
    public MandrillType visitPrintInteger(MandrillParser.PrintIntegerContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitPrintChar(MandrillParser.PrintCharContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitTargetVariable(MandrillParser.TargetVariableContext ctx) {
        String varName = ctx.Identifier().getText();
        boolean isArrayAccess = ctx.LeftBracket() != null;

        if (!symbolTable.isDeclared(varName)) {
            error(ctx, "Undefined variable '" + varName + "'");
            return MandrillType.UNKNOWN;
        }

        MandrillType varType = symbolTable.getType(varName);

        if (isArrayAccess) {
            if (varType != MandrillType.UNKNOWN && varType != MandrillType.ARRAY_PTR) {
                error(ctx, "Array index access requires an array variable");
            }

            if (ctx.expression() != null) {
                MandrillType indexType = visit(ctx.expression());
                if (indexType != MandrillType.INT) {
                    error(ctx, "Array index must be an integer");
                }
            }

            return MandrillType.INT;
        } else {
            return varType;
        }
    }

    @Override
    public MandrillType visitSourceVariable(MandrillParser.SourceVariableContext ctx) {
        String varName = ctx.Identifier().getText();
        boolean isArrayAccess = ctx.LeftBracket() != null;

        if (!symbolTable.isDeclared(varName)) {
            error(ctx, "Undefined variable '" + varName + "'");
            return MandrillType.UNKNOWN;
        }

        MandrillType varType = symbolTable.getType(varName);

        if (isArrayAccess) {
            if (varType != MandrillType.UNKNOWN && varType != MandrillType.ARRAY_PTR) {
                error(ctx, "Array index access requires an array variable");
            }

            if (ctx.expression() != null) {
                MandrillType indexType = visit(ctx.expression());
                if (indexType != MandrillType.INT) {
                    error(ctx, "Array index must be an integer");
                }
            }

            return MandrillType.INT;
        } else {
            return varType;
        }
    }

    @Override
    public MandrillType visitFunctionCall(MandrillParser.FunctionCallContext ctx) {
        String funcName = ctx.Identifier().getText();

        if (!symbolTable.isDeclared(funcName)) {
            error(ctx, "Undefined function '" + funcName + "'");
            return MandrillType.UNKNOWN;
        }

        MandrillType funcType = symbolTable.getType(funcName);

        MandrillParser.ArgumentListContext argList = ctx.argumentList();
        MandrillParser.FunctionDefContext funcDef = findFunctionDef(ctx, funcName);

        if (funcDef != null) {
            MandrillParser.ParameterListContext paramList = funcDef.parameterList();

            int paramCount = paramList != null ? paramList.parameter().size() : 0;
            int argCount = argList != null ? argList.expression().size() : 0;

            if (paramCount != argCount) {
                error(ctx, "Function '" + funcName + "' expects " + paramCount + " arguments, but got " + argCount);
            }

            if (paramList != null && argList != null) {
                List<MandrillParser.ParameterContext> params = paramList.parameter();
                List<MandrillParser.ExpressionContext> args = argList.expression();

                for (int i = 0; i < params.size() && i < args.size(); i++) {
                    MandrillParser.ParameterContext param = params.get(i);
                    MandrillParser.ExpressionContext arg = args.get(i);

                    boolean paramIsArray = param.arraySuffix() != null;
                    MandrillType argType = visit(arg);

                    if (paramIsArray && argType != MandrillType.ARRAY_PTR) {
                        error(arg, "Function '" + funcName + "' expects array argument for parameter " + (i + 1)
                                + ", but got integer");
                    } else if (!paramIsArray && argType == MandrillType.ARRAY_PTR) {
                        error(arg, "Function '" + funcName + "' expects integer argument for parameter " + (i + 1)
                                + ", but got array");
                    }
                }
            }
        }

        return funcType;
    }

    private MandrillParser.FunctionDefContext findFunctionDef(ParserRuleContext ctx, String funcName) {
        MandrillParser.ProgramContext program = findProgramContext(ctx);
        if (program != null) {
            for (MandrillParser.FunctionDefContext funcDef : program.functionDef()) {
                if (funcDef.Identifier().getText().equals(funcName)) {
                    return funcDef;
                }
            }
        }
        return null;
    }

    private MandrillParser.ProgramContext findProgramContext(ParserRuleContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        while (parent != null) {
            if (parent instanceof MandrillParser.ProgramContext) {
                return (MandrillParser.ProgramContext) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    @Override
    public MandrillType visitIntLiteral(MandrillParser.IntLiteralContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitCharLiteral(MandrillParser.CharLiteralContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitStringLiteral(MandrillParser.StringLiteralContext ctx) {
        return MandrillType.ARRAY_PTR;
    }

    @Override
    public MandrillType visitInputInt(MandrillParser.InputIntContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitInputChat(MandrillParser.InputChatContext ctx) {
        return MandrillType.INT;
    }

    @Override
    public MandrillType visitSubgroupExpression(MandrillParser.SubgroupExpressionContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public MandrillType visitMulDivModExpression(MandrillParser.MulDivModExpressionContext ctx) {
        MandrillType left = visit(ctx.expression(0));
        MandrillType right = visit(ctx.expression(1));

        if (left != MandrillType.INT) {
            error(ctx.expression(0), "Arithmetic operation requires integer operands");
        }
        if (right != MandrillType.INT) {
            error(ctx.expression(1), "Arithmetic operation requires integer operands");
        }

        return MandrillType.INT;
    }

    @Override
    public MandrillType visitAddSubExpression(MandrillParser.AddSubExpressionContext ctx) {
        MandrillType left = visit(ctx.expression(0));
        MandrillType right = visit(ctx.expression(1));

        if (left != MandrillType.INT) {
            error(ctx.expression(0), "Arithmetic operation requires integer operands");
        }
        if (right != MandrillType.INT) {
            error(ctx.expression(1), "Arithmetic operation requires integer operands");
        }

        return MandrillType.INT;
    }

    @Override
    public MandrillType visitComparingExpression(MandrillParser.ComparingExpressionContext ctx) {
        MandrillType left = visit(ctx.expression(0));
        MandrillType right = visit(ctx.expression(1));

        if (left != MandrillType.INT) {
            error(ctx.expression(0), "Comparison requires integer operands");
        }
        if (right != MandrillType.INT) {
            error(ctx.expression(1), "Comparison requires integer operands");
        }

        return MandrillType.INT;
    }

    @Override
    public MandrillType visitEqualityExpression(MandrillParser.EqualityExpressionContext ctx) {
        MandrillType left = visit(ctx.expression(0));
        MandrillType right = visit(ctx.expression(1));

        if (left != MandrillType.INT) {
            error(ctx.expression(0), "Equality check requires integer operands");
        }
        if (right != MandrillType.INT) {
            error(ctx.expression(1), "Equality check requires integer operands");
        }

        return MandrillType.INT;
    }

    @Override
    public MandrillType visitLoopStatement(MandrillParser.LoopStatementContext ctx) {
        MandrillType condType = visit(ctx.expr);
        if (condType != MandrillType.INT) {
            error(ctx.expr, "Loop condition must be an integer expression");
        }

        loopNestingLevel++;
        visit(ctx.stmtBlock());
        loopNestingLevel--;
        return MandrillType.UNKNOWN;
    }

    @Override
    public MandrillType visitConditionStatement(MandrillParser.ConditionStatementContext ctx) {
        MandrillType condType = visit(ctx.expr);
        if (condType != MandrillType.INT) {
            error(ctx.expr, "Condition must be an integer expression");
        }

        visit(ctx.thenStatement);
        if (ctx.elseStatement != null) {
            visit(ctx.elseStatement);
        }

        return MandrillType.UNKNOWN;
    }

    @Override
    public MandrillType visitJumpStmt(MandrillParser.JumpStmtContext ctx) {
        if (ctx.Break() != null) {
            if (loopNestingLevel == 0) {
                error(ctx, "break statement must be inside a loop");
            }
        } else if (ctx.Continue() != null) {
            if (loopNestingLevel == 0) {
                error(ctx, "continue statement must be inside a loop");
            }
        } else if (ctx.Return() != null) {
            if (functionNestingLevel == 0) {
                error(ctx, "return statement must be inside a function");
            } else if (ctx.expression() != null) {
                MandrillType returnType = visit(ctx.expression());

                ParserRuleContext parent = ctx.getParent();
                while (parent != null) {
                    if (parent instanceof MandrillParser.FunctionDefContext) {
                        MandrillParser.FunctionDefContext funcDef = (MandrillParser.FunctionDefContext) parent;
                        boolean funcIsArray = funcDef.arraySuffix() != null;

                        if (funcIsArray && returnType != MandrillType.ARRAY_PTR) {
                            error(ctx, "Function returns array, but return expression is not an array");
                        } else if (!funcIsArray && returnType == MandrillType.ARRAY_PTR) {
                            error(ctx, "Function returns integer, but return expression is an array");
                        }
                        break;
                    }
                    parent = parent.getParent();
                }
            }
        }

        return MandrillType.UNKNOWN;
    }
}