package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class CompilerImpl implements Compiler {
    private List<String> instructions = new ArrayList<>();
    private SymbolTable table;
    private long breakTarget = 0;
    private long continueTarget = 0;
    private boolean inFunction = false;

    public String compile(InputStream inputStream) throws IOException {
        Compiler.CompileContext context = Compiler.frontend(inputStream);
        this.table = context.table();
        instructions.clear();

        generateCode(context.tree());

        StringBuilder sb = new StringBuilder();
        for (String instr : instructions) {
            sb.append(instr).append("\n");
        }
        return sb.toString();
    }

    private void generateCode(MandrillParser.ProgramContext ctx) {
        List<MandrillParser.FunctionDefContext> functions = new ArrayList<>();
        List<MandrillParser.StatementContext> statements = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if (ctx.getChild(i) instanceof MandrillParser.FunctionDefContext) {
                functions.add((MandrillParser.FunctionDefContext) ctx.getChild(i));
            } else if (ctx.getChild(i) instanceof MandrillParser.StatementContext) {
                statements.add((MandrillParser.StatementContext) ctx.getChild(i));
            }
        }

        int mainStart = instructions.size();
        for (MandrillParser.StatementContext stmt : statements) {
            visitStatement(stmt);
        }
        int mainSize = instructions.size() - mainStart;
        instructions.subList(mainStart, instructions.size()).clear();

        int funcCodeSize = 0;
        for (MandrillParser.FunctionDefContext funcCtx : functions) {
            funcCodeSize += 4 + funcCtx.stmtBlock().statement().size() * 2;
        }

        long funcBaseAddr = (mainSize + 2 + funcCodeSize) * 8L;
        for (MandrillParser.FunctionDefContext funcCtx : functions) {
            String funcName = funcCtx.Identifier().getText();
            FunctionSymbol func = table.getFunction(funcName);
            func.setAddress(funcBaseAddr);
        }

        for (MandrillParser.StatementContext stmt : statements) {
            visitStatement(stmt);
        }

        int jumpIdx = instructions.size();
        emit("jump", 0L);

        for (MandrillParser.FunctionDefContext funcCtx : functions) {
            visitFunctionDef(funcCtx);
        }

        long infiniteLoopAddr = (long) instructions.size() * 8L;
        emit("jump", infiniteLoopAddr);
        instructions.set(jumpIdx, "jump " + infiniteLoopAddr);
    }

    private void emit(String instr) {
        instructions.add(instr);
    }

    private void emit(String op, long operand) {
        instructions.add(op + " " + operand);
    }

    private void emitEval(int op) {
        emit("eval", op);
    }

    private void visitFunctionDef(MandrillParser.FunctionDefContext ctx) {
        String funcName = ctx.Identifier().getText();
        FunctionSymbol func = table.getFunction(funcName);
        func.setAddress((long) instructions.size() * 8L);

        emit("# function: " + funcName);
        emit("nop");

        for (int i = 0; i < func.getParamCount(); i++) {
            emit("dlwrite", i);
        }

        table.setCurrentFunction(func);
        table.enterScope();
        for (VariableSymbol param : func.getParameters()) {
            table.restoreParameter(param);
        }
        inFunction = true;
        visitStmtBlock(ctx.stmtBlock());
        inFunction = false;
        table.exitScope();
        table.setCurrentFunction(null);
    }

    private void visitStmtBlock(MandrillParser.StmtBlockContext ctx) {
        for (int i = 0; i < ctx.statement().size(); i++) {
            visitStatement(ctx.statement(i));
        }
    }

    private void visitStatement(MandrillParser.StatementContext ctx) {
        if (ctx.assignStatement() != null) {
            visitAssignStatement(ctx.assignStatement());
        } else if (ctx.loopStatement() != null) {
            visitLoopStatement(ctx.loopStatement());
        } else if (ctx.conditionStatement() != null) {
            visitConditionStatement(ctx.conditionStatement());
        } else if (ctx.jumpStmt() != null) {
            visitJumpStmt(ctx.jumpStmt());
        } else if (ctx.stmtBlock() != null) {
            visitStmtBlock(ctx.stmtBlock());
        } else if (ctx.declarationStmt() != null) {
        }
    }

    private void visitAssignStatement(MandrillParser.AssignStatementContext ctx) {
        visitRvalue(ctx.rvalue());
        visitLvalue(ctx.lvalue());
    }

    private void visitLvalue(MandrillParser.LvalueContext ctx) {
        if (ctx instanceof MandrillParser.PrintIntegerContext) {
            emit("puti", 0L);
        } else if (ctx instanceof MandrillParser.PrintCharContext) {
            emit("putc", 0L);
        } else if (ctx instanceof MandrillParser.TargetVariableContext) {
            MandrillParser.TargetVariableContext targetCtx = (MandrillParser.TargetVariableContext) ctx;
            String name = targetCtx.Identifier().getText();
            Symbol symbol = table.lookup(name);
            if (symbol instanceof VariableSymbol) {
                VariableSymbol var = (VariableSymbol) symbol;
                if (var.getScope() == Symbol.Scope.GLOBAL) {
                    if (targetCtx.expression() != null) {
                        emit("dload", var.getOffset());
                        visitExpression(targetCtx.expression());
                        emitEval(Constants.EVAL_ADD);
                        emit("dwrite", 0L);
                    } else {
                        emit("dwrite", var.getOffset());
                    }
                } else {
                    if (targetCtx.expression() != null) {
                        emit("dlload", var.getOffset());
                        visitExpression(targetCtx.expression());
                        emitEval(Constants.EVAL_ADD);
                        emit("dlwrite", 0L);
                    } else {
                        emit("dlwrite", var.getOffset());
                    }
                }
            }
        }
    }

    private void visitRvalue(MandrillParser.RvalueContext ctx) {
        visitExpression(ctx.expression());
    }

    private void visitExpression(MandrillParser.ExpressionContext ctx) {
        if (ctx instanceof MandrillParser.IntLiteralContext) {
            MandrillParser.IntLiteralContext intCtx = (MandrillParser.IntLiteralContext) ctx;
            emit("dstore", Long.parseLong(intCtx.IntegerConstant().getText()));
        } else if (ctx instanceof MandrillParser.CharLiteralContext) {
            MandrillParser.CharLiteralContext charCtx = (MandrillParser.CharLiteralContext) ctx;
            String charStr = charCtx.CharacterConstant().getText();
            emit("dstore", (long) charStr.charAt(1));
        } else if (ctx instanceof MandrillParser.StringLiteralContext) {
            MandrillParser.StringLiteralContext strCtx = (MandrillParser.StringLiteralContext) ctx;
            String str = strCtx.StringConstant().getText();
            str = str.substring(1, str.length() - 1);
            for (int i = str.length() - 1; i >= 0; i--) {
                emit("dstore", (long) str.charAt(i));
            }
            emit("dstore", (long) str.length());
        } else if (ctx instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext varCtx = (MandrillParser.SourceVariableContext) ctx;
            String name = varCtx.Identifier().getText();
            Symbol symbol = table.lookup(name);
            if (symbol instanceof VariableSymbol) {
                VariableSymbol var = (VariableSymbol) symbol;
                if (var.getScope() == Symbol.Scope.GLOBAL) {
                    if (varCtx.expression() != null) {
                        emit("dload", var.getOffset());
                        visitExpression(varCtx.expression());
                        emitEval(Constants.EVAL_ADD);
                        emit("dload", 0L);
                    } else {
                        emit("dload", var.getOffset());
                    }
                } else {
                    if (varCtx.expression() != null) {
                        emit("dlload", var.getOffset());
                        visitExpression(varCtx.expression());
                        emitEval(Constants.EVAL_ADD);
                        emit("dlload", 0L);
                    } else {
                        emit("dlload", var.getOffset());
                    }
                }
            }
        } else if (ctx instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext callCtx = (MandrillParser.FunctionCallContext) ctx;
            visitFunctionCall(callCtx);
        } else if (ctx instanceof MandrillParser.InputIntContext) {
            emit("geti", 0L);
        } else if (ctx instanceof MandrillParser.InputChatContext) {
            emit("getc", 0L);
        } else if (ctx instanceof MandrillParser.SubgroupExpressionContext) {
            MandrillParser.SubgroupExpressionContext subCtx = (MandrillParser.SubgroupExpressionContext) ctx;
            visitExpression(subCtx.expression());
        } else if (ctx instanceof MandrillParser.MulDivModExpressionContext) {
            MandrillParser.MulDivModExpressionContext mulCtx = (MandrillParser.MulDivModExpressionContext) ctx;
            visitExpression(mulCtx.expression(0));
            visitExpression(mulCtx.expression(1));
            String op = mulCtx.op.getText();
            if ("*".equals(op)) {
                emitEval(Constants.EVAL_MUL);
            } else if ("/".equals(op)) {
                emitEval(Constants.EVAL_DIV);
            } else if ("%".equals(op)) {
                emitEval(Constants.EVAL_MOD);
            }
        } else if (ctx instanceof MandrillParser.AddSubExpressionContext) {
            MandrillParser.AddSubExpressionContext addCtx = (MandrillParser.AddSubExpressionContext) ctx;
            visitExpression(addCtx.expression(0));
            visitExpression(addCtx.expression(1));
            String op = addCtx.op.getText();
            if ("+".equals(op)) {
                emitEval(Constants.EVAL_ADD);
            } else if ("-".equals(op)) {
                emitEval(Constants.EVAL_MINUS);
            }
        } else if (ctx instanceof MandrillParser.ComparingExpressionContext) {
            MandrillParser.ComparingExpressionContext compCtx = (MandrillParser.ComparingExpressionContext) ctx;
            visitExpression(compCtx.expression(0));
            visitExpression(compCtx.expression(1));
            String op = compCtx.op.getText();
            if ("<".equals(op)) {
                emitEval(Constants.EVAL_LESS);
            } else if (">".equals(op)) {
                emitEval(Constants.EVAL_GREATER);
            } else if ("<=".equals(op)) {
                emitEval(Constants.EVAL_LESS_OR_EQUAL);
            } else if (">=".equals(op)) {
                emitEval(Constants.EVAL_GREATER_OR_EQUAL);
            }
        } else if (ctx instanceof MandrillParser.EqualityExpressionContext) {
            MandrillParser.EqualityExpressionContext eqCtx = (MandrillParser.EqualityExpressionContext) ctx;
            visitExpression(eqCtx.expression(0));
            visitExpression(eqCtx.expression(1));
            String op = eqCtx.op.getText();
            if ("==".equals(op)) {
                emitEval(Constants.EVAL_EQUAL);
            } else if ("!=".equals(op)) {
                emitEval(Constants.EVAL_NOT_EQUAL);
            }
        }
    }

    private void visitFunctionCall(MandrillParser.FunctionCallContext ctx) {
        String funcName = ctx.Identifier().getText();
        FunctionSymbol func = table.getFunction(funcName);

        if (ctx.argumentList() != null) {
            List<MandrillParser.ExpressionContext> exprs = ctx.argumentList().expression();
            for (int i = exprs.size() - 1; i >= 0; i--) {
                visitExpression(exprs.get(i));
            }
        }

        int localSize = (func.getParamCount() + func.getLocalCount()) * 4;
        emit("dstore", (long) localSize);
        emit("jal", func.getAddress());
    }

    private void visitJumpStmt(MandrillParser.JumpStmtContext ctx) {
        String keyword = ctx.getChild(0).getText();
        if (keyword.equals("return")) {
            visitExpression(ctx.expression());
            emit("ret", 0L);
        } else if (keyword.equals("break")) {
            emit("jump", breakTarget);
        } else if (keyword.equals("continue")) {
            emit("jump", continueTarget);
        }
    }

    private void visitLoopStatement(MandrillParser.LoopStatementContext ctx) {
        long loopStart = (long) instructions.size() * 8L;
        visitExpression(ctx.expr);
        int falseTargetIdx = instructions.size();
        emit("dstore", 0L);
        int trueTargetIdx = instructions.size();
        emit("dstore", 0L);
        emitEval(Constants.EVAL_CONDITION);
        long trueTarget = (long) instructions.size() * 8L;
        continueTarget = loopStart;
        visitStmtBlock(ctx.stmtBlock());
        emit("jump", loopStart);
        long falseTarget = (long) instructions.size() * 8L;
        instructions.set(falseTargetIdx, "dstore " + falseTarget);
        instructions.set(trueTargetIdx, "dstore " + trueTarget);
        breakTarget = falseTarget;
    }

    private void visitConditionStatement(MandrillParser.ConditionStatementContext ctx) {
        visitExpression(ctx.expr);
        int exprEndIdx = instructions.size();
        int falseTargetIdx = instructions.size();
        emit("dstore", 0L);
        int trueTargetIdx = instructions.size();
        emit("dstore", 0L);
        emitEval(Constants.EVAL_CONDITION);

        List<String> thenCode = new ArrayList<>();
        int oldSize = instructions.size();
        visitStmtBlock(ctx.thenStatement);
        thenCode.addAll(instructions.subList(oldSize, instructions.size()));
        instructions.subList(oldSize, instructions.size()).clear();

        int thenCount = thenCode.size();
        long thenTarget = (long) (exprEndIdx + 3) * 8L;

        int elseStartIdx = exprEndIdx + 3 + thenCount;
        if (ctx.elseStatement != null) {
            elseStartIdx++;
        }
        long elseTarget = (long) elseStartIdx * 8L;

        emit("dstore", thenTarget);
        emit("dstore", elseTarget);
        emitEval(Constants.EVAL_CONDITION);

        instructions.addAll(thenCode);

        if (ctx.elseStatement != null) {
            int currentSize = instructions.size();
            long afterElse = (long) (currentSize + 1) * 8L;
            emit("jump", afterElse);
            visitStmtBlock(ctx.elseStatement);
        }
    }
}