package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillBaseVisitor;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.Constants;

import java.util.*;

public class CodeGenerator extends MandrillBaseVisitor<Void> {
    private final SymbolTable symbolTable;
    private final StringBuilder code;
    private int labelCounter = 0;
    private int instructionCounter = 0;
    private final Map<String, Integer> functionStartAddresses;
    private final Map<String, Integer> functionParamCounts;
    private final Map<String, Integer> functionLocalCounts;
    private boolean inConditionExpression = false;
    private final Map<String, Integer> labelToAddresses;
    private String currentLoopStartLabel = null;
    private String currentLoopEndLabel = null;
    private int currentFunctionParamCount = 0;
    private int currentFunctionLocalCount = 0;
    private final List<String> stringLiterals;
    private int tempVarIndex = 1000;

    public CodeGenerator(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.code = new StringBuilder();
        this.functionStartAddresses = new HashMap<>();
        this.functionParamCounts = new HashMap<>();
        this.functionLocalCounts = new HashMap<>();
        this.labelToAddresses = new HashMap<>();
        this.stringLiterals = new ArrayList<>();
    }

    public String generate(MandrillParser.ProgramContext tree) {
        visit(tree);

        String generated = code.toString();
        
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(labelToAddresses.entrySet());
        entries.sort((a, b) -> b.getKey().length() - a.getKey().length());
        
        for (Map.Entry<String, Integer> entry : entries) {
            String label = entry.getKey();
            int addr = entry.getValue();
            long addressValue = (long) addr * 8;

            String jumpTargetStr = "jump " + label;
            String jumpReplaceStr = "jump " + addressValue;
            generated = generated.replace(jumpTargetStr, jumpReplaceStr);

            String dconstTargetStr = "dconst_label " + label;
            String dconstReplaceStr = "dconst " + addressValue;
            generated = generated.replace(dconstTargetStr, dconstReplaceStr);

            String labelStr = "label_" + label + "\n";
            generated = generated.replace(labelStr, "");
        }

        return generated;
    }

    private String newLabel() {
        String label = "L" + (labelCounter++);
        labelToAddresses.put(label, -1);
        return label;
    }

    private void emit(String instr) {
        code.append(instr).append("\n");
        instructionCounter++;
    }

    private void emitLabel(String label) {
        labelToAddresses.put(label, instructionCounter);
        code.append("label_").append(label).append("\n");
    }

    private void emitLabelOnly(String label) {
        labelToAddresses.put(label, instructionCounter);
        emit("dconst_label_" + label + "_" + instructionCounter);
    }

    private void emitJump(String targetLabel) {
        emit("jump " + targetLabel);
    }

    @Override
    public Void visitProgram(MandrillParser.ProgramContext ctx) {
        for (MandrillParser.FunctionDefContext funcCtx : ctx.functionDef()) {
            String funcName = funcCtx.Identifier().getText();
            int paramCount = 0;
            if (funcCtx.parameterList() != null) {
                paramCount = funcCtx.parameterList().parameter().size();
            }
            functionParamCounts.put(funcName, paramCount);
        }

        if (!ctx.functionDef().isEmpty()) {
            emit("jump MAIN_START");
        }

        for (MandrillParser.FunctionDefContext funcCtx : ctx.functionDef()) {
            String funcName = funcCtx.Identifier().getText();
            functionStartAddresses.put(funcName, instructionCounter);
            visitFunctionDef(funcCtx);
        }

        emitLabel("MAIN_START");

        if (currentFunctionLocalCount > 0) {
            emitConstant(currentFunctionLocalCount * 4);
            emit("malloc 0");
            emit("setsp 0");
        }

        for (MandrillParser.StatementContext stmtCtx : ctx.statement()) {
            visitStatement(stmtCtx);
        }

        emit("jump 0xFFFFFFFF");

        return null;
    }

    private int estimateFunctionSize(MandrillParser.FunctionDefContext ctx) {
        int size = 0;
        if (ctx.parameterList() != null) {
            size += ctx.parameterList().parameter().size();
        }
        if (ctx.stmtBlock() != null) {
            for (MandrillParser.StatementContext stmtCtx : ctx.stmtBlock().statement()) {
                size += estimateStatementSize(stmtCtx);
            }
        }
        return size;
    }

    private int estimateStatementSize(MandrillParser.StatementContext ctx) {
        int size = 0;
        if (ctx.declarationStmt() != null) {
            size = 1;
        } else if (ctx.assignStatement() != null) {
            MandrillParser.AssignStatementContext assignCtx = ctx.assignStatement();
            size = 1;
            if (assignCtx.rvalue() != null && assignCtx.rvalue().expression() != null) {
                size += estimateExpressionSize(assignCtx.rvalue().expression());
            }
        } else if (ctx.loopStatement() != null) {
            MandrillParser.LoopStatementContext loopCtx = ctx.loopStatement();
            size = 2;
            if (loopCtx.expr != null) {
                size += estimateExpressionSize(loopCtx.expr) + 3;
            }
            if (loopCtx.stmtBlock() != null) {
                for (MandrillParser.StatementContext stmtCtx : loopCtx.stmtBlock().statement()) {
                    size += estimateStatementSize(stmtCtx);
                }
            }
        } else if (ctx.conditionStatement() != null) {
            MandrillParser.ConditionStatementContext condCtx = ctx.conditionStatement();
            size = 2;
            if (condCtx.expr != null) {
                size += estimateExpressionSize(condCtx.expr) + 3;
            }
            if (condCtx.thenStatement != null) {
                for (MandrillParser.StatementContext stmtCtx : condCtx.thenStatement.statement()) {
                    size += estimateStatementSize(stmtCtx);
                }
            }
            if (condCtx.elseStatement != null) {
                for (MandrillParser.StatementContext stmtCtx : condCtx.elseStatement.statement()) {
                    size += estimateStatementSize(stmtCtx);
                }
            }
        } else if (ctx.jumpStmt() != null) {
            MandrillParser.JumpStmtContext jumpCtx = ctx.jumpStmt();
            size = 1;
            if (jumpCtx.expression() != null) {
                size += estimateExpressionSize(jumpCtx.expression());
            }
        } else if (ctx.stmtBlock() != null) {
            for (MandrillParser.StatementContext stmtCtx : ctx.stmtBlock().statement()) {
                size += estimateStatementSize(stmtCtx);
            }
        }
        return size;
    }

    private int estimateExpressionSize(MandrillParser.ExpressionContext ctx) {
        return 1;
    }

    @Override
    public Void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        String funcName = ctx.Identifier().getText();

        symbolTable.enterScope();
        currentFunctionParamCount = 0;
        currentFunctionLocalCount = 0;

        if (ctx.parameterList() != null) {
            for (MandrillParser.ParameterContext paramCtx : ctx.parameterList().parameter()) {
                String paramName = paramCtx.Identifier().getText();
                boolean isArray = paramCtx.arraySuffix() != null;
                symbolTable.addParameter(paramName, isArray);
                currentFunctionParamCount++;
            }
        }

        emitLabel(funcName);

        if (currentFunctionParamCount > 0) {
            for (int i = 0; i < currentFunctionParamCount; i++) {
                emit("dlwrite " + i);
            }
        }

        if (ctx.stmtBlock() != null) {
            for (MandrillParser.StatementContext stmtCtx : ctx.stmtBlock().statement()) {
                visitStatement(stmtCtx);
            }
        }

        emit("dconst 0");
        emit("ret 0");

        functionParamCounts.put(funcName, currentFunctionParamCount);
        functionLocalCounts.put(funcName, currentFunctionLocalCount);
        symbolTable.exitScope();
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
        boolean isArray = ctx.arraySuffix() != null;
        
        if (ctx.scope != null && ctx.scope.getText().equals("local")) {
            SymbolTable.SymbolInfo info = symbolTable.addLocalVariable(varName, isArray);
            currentFunctionLocalCount++;
        } else if (ctx.scope != null && ctx.scope.getText().equals("global")) {
            symbolTable.addGlobalVariable(varName, isArray);
        }
        
        return null;
    }

    @Override
    public Void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        if (ctx.lvalue() != null) {
            MandrillParser.LvalueContext lvalueCtx = ctx.lvalue();
            if (lvalueCtx instanceof MandrillParser.PrintIntegerContext) {
                if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
                    MandrillParser.ExpressionContext rhsExpr = ctx.rvalue().expression();
                    if (rhsExpr instanceof MandrillParser.SourceVariableContext) {
                        MandrillParser.SourceVariableContext sourceVarCtx = (MandrillParser.SourceVariableContext) rhsExpr;
                        String varName = sourceVarCtx.Identifier().getText();
                        SymbolTable.SymbolInfo info = symbolTable.lookup(varName);
                        if (info != null && info.valueType == SymbolTable.ValueType.ARRAY_TYPE) {
                            visitExpression(rhsExpr);
                            emit("puts 0");
                            return null;
                        }
                    }
                    visitExpression(rhsExpr);
                    emit("puti 0");
                }
            } else if (lvalueCtx instanceof MandrillParser.PrintCharContext) {
                if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
                    visitExpression(ctx.rvalue().expression());
                    emit("putc 0");
                }
            } else if (lvalueCtx instanceof MandrillParser.TargetVariableContext) {
                MandrillParser.TargetVariableContext targetCtx = (MandrillParser.TargetVariableContext) lvalueCtx;
                String varName = targetCtx.Identifier().getText();
                SymbolTable.SymbolInfo info = symbolTable.lookup(varName);

                if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
                    MandrillParser.ExpressionContext rhsExpr = ctx.rvalue().expression();

                    if (rhsExpr instanceof MandrillParser.StringLiteralContext) {
                        String strValue = rhsExpr.getText();
                        strValue = strValue.substring(1, strValue.length() - 1);
                        int stringIndex = stringLiterals.size();
                        stringLiterals.add(strValue);
                        emitConstant((strValue.length() + 1) * 4);
                        emit("malloc 0");
                        int tempIndex = tempVarIndex++;
                        emit("dwrite " + tempIndex);
                        int i = 0;
                        for (char c : strValue.toCharArray()) {
                            emit("dload " + tempIndex);
                            emitConstant((int) c);
                            emit("dawrite " + (i * 4));
                            i++;
                        }
                        emit("dload " + tempIndex);
                        emitConstant(0);
                        emit("dawrite " + (i * 4));
                        emit("dload " + tempIndex);
                    } else {
                        visitExpression(rhsExpr);
                    }

                    if (targetCtx.expression() != null) {
                        visitExpression(targetCtx.expression());
                        emit("daload 0");
                        emit("swap");
                        emit("dawrite 0");
                    } else if (info != null && info.kind == SymbolTable.SymbolKind.GLOBAL_VAR) {
                        emit("dwrite " + info.index);
                    } else if (info != null && (info.kind == SymbolTable.SymbolKind.LOCAL_VAR
                            || info.kind == SymbolTable.SymbolKind.PARAM)) {
                        emit("dlwrite " + info.localOffset);
                    }
                }
            }
        } else if (ctx.Identifier() != null) {
            String varName = ctx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookup(varName);

            if (ctx.rvalue() != null && ctx.rvalue().expression() != null) {
                MandrillParser.ExpressionContext rhsExpr = ctx.rvalue().expression();
                
                if (rhsExpr instanceof MandrillParser.StringLiteralContext) {
                    String strValue = rhsExpr.getText();
                    strValue = strValue.substring(1, strValue.length() - 1);
                    int stringIndex = stringLiterals.size();
                    stringLiterals.add(strValue);
                    emitConstant((strValue.length() + 1) * 4);
                    emit("malloc 0");
                    int tempIndex = tempVarIndex++;
                    emit("dwrite " + tempIndex);
                    int i = 0;
                    for (char c : strValue.toCharArray()) {
                        emit("dload " + tempIndex);
                        emitConstant((int) c);
                        emit("dawrite " + (i * 4));
                        i++;
                    }
                    emit("dload " + tempIndex);
                    emitConstant(0);
                    emit("dawrite " + (i * 4));
                    emit("dload " + tempIndex);
                } else {
                    visitExpression(rhsExpr);
                    emit("malloc 0");
                }

                if (info != null && info.kind == SymbolTable.SymbolKind.GLOBAL_VAR) {
                    emit("dwrite " + info.index);
                } else if (info != null && (info.kind == SymbolTable.SymbolKind.LOCAL_VAR
                        || info.kind == SymbolTable.SymbolKind.PARAM)) {
                    emit("dlwrite " + info.localOffset);
                }
            }
        }
        return null;
    }

    @Override
    public Void visitLoopStatement(MandrillParser.LoopStatementContext ctx) {
        String loopStart = newLabel();
        String loopBody = newLabel();
        String loopEnd = newLabel();

        String oldLoopStartLabel = currentLoopStartLabel;
        String oldLoopEndLabel = currentLoopEndLabel;
        currentLoopStartLabel = loopStart;
        currentLoopEndLabel = loopEnd;

        emitLabel(loopStart);
        if (ctx.expr != null) {
            inConditionExpression = true;
            visitExpression(ctx.expr);
            inConditionExpression = false;
            emit("dconst_label " + loopBody);
            emit("dconst_label " + loopEnd);
            emit("eval " + Constants.EVAL_CONDITION);
        }
        emitLabel(loopBody);
        if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        }
        emitJump(loopStart);
        emitLabel(loopEnd);
        emit("nop 0");

        currentLoopStartLabel = oldLoopStartLabel;
        currentLoopEndLabel = oldLoopEndLabel;
        return null;
    }

    @Override
    public Void visitConditionStatement(MandrillParser.ConditionStatementContext ctx) {
        String thenLabel = newLabel();
        String elseLabel = newLabel();
        String endLabel = newLabel();

        if (ctx.expr != null) {
            inConditionExpression = true;
            visitExpression(ctx.expr);
            inConditionExpression = false;
            emit("dconst_label " + thenLabel);
            if (ctx.elseStatement != null) {
                emit("dconst_label " + elseLabel);
            } else {
                emit("dconst_label " + endLabel);
            }
            emit("eval " + Constants.EVAL_CONDITION);
        }

        emitLabel(thenLabel);
        if (ctx.thenStatement != null) {
            for (MandrillParser.StatementContext stmtCtx : ctx.thenStatement.statement()) {
                visitStatement(stmtCtx);
            }
        }

        if (ctx.elseStatement != null) {
            emitJump(endLabel);
            emitLabel(elseLabel);
            for (MandrillParser.StatementContext stmtCtx : ctx.elseStatement.statement()) {
                visitStatement(stmtCtx);
            }
        }

        emitLabel(endLabel);
        emit("nop 0");
        return null;
    }

    @Override
    public Void visitJumpStmt(MandrillParser.JumpStmtContext ctx) {
        if (ctx.Return() != null) {
            if (ctx.expression() != null) {
                visitExpression(ctx.expression());
            }
            emit("ret 0");
        } else if (ctx.Break() != null && currentLoopEndLabel != null) {
            emitJump(currentLoopEndLabel);
        } else if (ctx.Continue() != null && currentLoopStartLabel != null) {
            emitJump(currentLoopStartLabel);
        }
        return null;
    }

    @Override
    public Void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        for (MandrillParser.StatementContext stmtCtx : ctx.statement()) {
            visitStatement(stmtCtx);
        }
        return null;
    }

    private void emitConstant(long value) {
        emit("dconst " + value);
    }

    private Void visitExpression(MandrillParser.ExpressionContext ctx) {
        if (ctx instanceof MandrillParser.IntLiteralContext) {
            MandrillParser.IntLiteralContext intCtx = (MandrillParser.IntLiteralContext) ctx;
            String intStr = intCtx.getText();
            emitConstant(Long.parseLong(intStr));
        } else if (ctx instanceof MandrillParser.CharLiteralContext) {
            MandrillParser.CharLiteralContext charCtx = (MandrillParser.CharLiteralContext) ctx;
            String charStr = charCtx.getText();
            charStr = charStr.substring(1, charStr.length() - 1);
            int charValue;
            if (charStr.equals("\\n")) {
                charValue = '\n';
            } else if (charStr.equals("\\\\")) {
                charValue = '\\';
            } else if (charStr.equals("\\'")) {
                charValue = '\'';
            } else {
                charValue = charStr.charAt(0);
            }
            emitConstant(charValue);
        } else if (ctx instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext sourceVarCtx = (MandrillParser.SourceVariableContext) ctx;
            String varName = sourceVarCtx.Identifier().getText();
            SymbolTable.SymbolInfo info = symbolTable.lookup(varName);

            if (sourceVarCtx.expression() != null) {
                if (info != null && info.kind == SymbolTable.SymbolKind.GLOBAL_VAR) {
                    emit("dload " + info.index);
                } else if (info != null && (info.kind == SymbolTable.SymbolKind.LOCAL_VAR
                        || info.kind == SymbolTable.SymbolKind.PARAM)) {
                    emit("dlload " + info.localOffset);
                }
                visitExpression(sourceVarCtx.expression());
                emit("daload 0");
            } else {
                if (info != null && info.kind == SymbolTable.SymbolKind.GLOBAL_VAR) {
                    emit("dload " + info.index);
                } else if (info != null && (info.kind == SymbolTable.SymbolKind.LOCAL_VAR
                        || info.kind == SymbolTable.SymbolKind.PARAM)) {
                    emit("dlload " + info.localOffset);
                }
            }
        } else if (ctx instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext funcCallCtx = (MandrillParser.FunctionCallContext) ctx;
            String funcName = funcCallCtx.Identifier().getText();

            if (funcCallCtx.argumentList() != null) {
                List<MandrillParser.ExpressionContext> args = funcCallCtx.argumentList().expression();
                for (int i = args.size() - 1; i >= 0; i--) {
                    visitExpression(args.get(i));
                }
            }

            int funcIndex = functionStartAddresses.getOrDefault(funcName, -1);
            int targetFuncParamCount = functionParamCounts.getOrDefault(funcName, 0);
            int targetFuncLocalCount = functionLocalCounts.getOrDefault(funcName, 0);
            emitConstant((targetFuncParamCount + targetFuncLocalCount) * 4);
            emit("jal " + (funcIndex * 8));
        } else if (ctx instanceof MandrillParser.InputIntContext) {
            emit("geti 0");
        } else if (ctx instanceof MandrillParser.InputChatContext) {
            emit("getc 0");
        } else if (ctx instanceof MandrillParser.AddSubExpressionContext) {
            MandrillParser.AddSubExpressionContext addSubCtx = (MandrillParser.AddSubExpressionContext) ctx;
            if (addSubCtx.Plus() != null && inConditionExpression && isBooleanExpression(addSubCtx.expression(0)) && isBooleanExpression(addSubCtx.expression(1))) {
                String hasValueLabel = newLabel();
                String needEvalLabel = newLabel();
                String endLabel = newLabel();
                visitExpression(addSubCtx.expression(0));
                emit("dconst_label " + hasValueLabel);
                emit("dconst_label " + needEvalLabel);
                emit("eval " + Constants.EVAL_CONDITION);
                emitLabel(hasValueLabel);
                emitConstant(1);
                emitJump(endLabel);
                emitLabel(needEvalLabel);
                visitExpression(addSubCtx.expression(1));
                emitLabel(endLabel);
            } else {
                if (addSubCtx.expression(0) != null)
                    visitExpression(addSubCtx.expression(0));
                if (addSubCtx.expression(1) != null)
                    visitExpression(addSubCtx.expression(1));
                if (addSubCtx.Plus() != null) {
                    emit("eval " + Constants.EVAL_ADD);
                } else if (addSubCtx.Minus() != null) {
                    emit("eval " + Constants.EVAL_MINUS);
                }
            }
        } else if (ctx instanceof MandrillParser.MulDivModExpressionContext) {
            MandrillParser.MulDivModExpressionContext mulDivCtx = (MandrillParser.MulDivModExpressionContext) ctx;
            if (mulDivCtx.Star() != null && inConditionExpression && isBooleanExpression(mulDivCtx.expression(0)) && isBooleanExpression(mulDivCtx.expression(1))) {
                String falseLabel = newLabel();
                String continueLabel = newLabel();
                String endLabel = newLabel();
                visitExpression(mulDivCtx.expression(0));
                emit("dconst_label " + continueLabel);
                emit("dconst_label " + falseLabel);
                emit("eval " + Constants.EVAL_CONDITION);
                emitLabel(falseLabel);
                emitConstant(0);
                emitJump(endLabel);
                emitLabel(continueLabel);
                visitExpression(mulDivCtx.expression(1));
                emitLabel(endLabel);
            } else {
                if (mulDivCtx.expression(0) != null)
                    visitExpression(mulDivCtx.expression(0));
                if (mulDivCtx.expression(1) != null)
                    visitExpression(mulDivCtx.expression(1));
                if (mulDivCtx.Star() != null) {
                    emit("eval " + Constants.EVAL_MUL);
                } else if (mulDivCtx.Slash() != null) {
                    emit("eval " + Constants.EVAL_DIV);
                } else if (mulDivCtx.Percentage() != null) {
                    emit("eval " + Constants.EVAL_MOD);
                }
            }
        } else if (ctx instanceof MandrillParser.ComparingExpressionContext) {
            MandrillParser.ComparingExpressionContext compareCtx = (MandrillParser.ComparingExpressionContext) ctx;
            if (compareCtx.expression(0) != null)
                visitExpression(compareCtx.expression(0));
            if (compareCtx.expression(1) != null)
                visitExpression(compareCtx.expression(1));
            if (compareCtx.LessThan() != null) {
                emit("eval " + Constants.EVAL_LESS);
            } else if (compareCtx.LargeThan() != null) {
                emit("eval " + Constants.EVAL_GREATER);
            } else if (compareCtx.NoLessThan() != null) {
                emit("eval " + Constants.EVAL_GREATER_OR_EQUAL);
            } else if (compareCtx.NoMoreThan() != null) {
                emit("eval " + Constants.EVAL_LESS_OR_EQUAL);
            }
        } else if (ctx instanceof MandrillParser.EqualityExpressionContext) {
            MandrillParser.EqualityExpressionContext eqCtx = (MandrillParser.EqualityExpressionContext) ctx;
            if (eqCtx.expression(0) != null)
                visitExpression(eqCtx.expression(0));
            if (eqCtx.expression(1) != null)
                visitExpression(eqCtx.expression(1));
            if (eqCtx.Equality() != null) {
                emit("eval " + Constants.EVAL_EQUAL);
            } else if (eqCtx.Inequality() != null) {
                emit("eval " + Constants.EVAL_NOT_EQUAL);
            }
        } else if (ctx instanceof MandrillParser.SubgroupExpressionContext) {
            MandrillParser.SubgroupExpressionContext subExprCtx = (MandrillParser.SubgroupExpressionContext) ctx;
            if (subExprCtx.expression() != null) {
                visitExpression(subExprCtx.expression());
            }
        }
        return null;
    }

    private boolean isBooleanExpression(MandrillParser.ExpressionContext ctx) {
        return ctx instanceof MandrillParser.ComparingExpressionContext 
            || ctx instanceof MandrillParser.EqualityExpressionContext;
    }
}
