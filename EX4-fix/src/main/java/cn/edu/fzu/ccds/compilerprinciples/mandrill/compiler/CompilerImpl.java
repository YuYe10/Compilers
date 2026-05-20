package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler.SymbolTable.FunctionSymbol;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler.SymbolTable.ValueKind;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler.SymbolTable.VariableSymbol;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.Constants;

import java.io.IOException;
import java.io.InputStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompilerImpl implements Compiler {

    @Override
    public String compile(InputStream inputStream) throws IOException {
        CompileContext context = Compiler.frontend(inputStream);
        AssemblyBuilder builder = new AssemblyBuilder(context.table());
        builder.compileProgram(context.tree());
        return builder.build();
    }

    private static final class AssemblyBuilder {
        private final SymbolTable table;
        private final List<InstructionLine> instructions = new ArrayList<>();
        private final Map<String, Label> labels = new LinkedHashMap<>();
        private final Deque<LoopContext> loopStack = new ArrayDeque<>();
        private int labelCounter = 0;

        private AssemblyBuilder(SymbolTable table) {
            this.table = table;
        }

        private String build() {
            StringBuilder builder = new StringBuilder();
            for (InstructionLine line : instructions) {
                long operand = line.labelRef == null ? line.operand : resolveLabel(line.labelRef);
                builder.append(line.mnemonic).append(' ').append(operand).append('\n');
            }
            return builder.toString();
        }

        private void compileProgram(MandrillParser.ProgramContext programContext) {
            Label mainLabel = newLabel("main");
            emit("jump", mainLabel);

            for (int index = 0; index < programContext.getChildCount(); index++) {
                ParseTree child = programContext.getChild(index);
                if (child instanceof MandrillParser.FunctionDefContext) {
                    MandrillParser.FunctionDefContext functionDefContext = (MandrillParser.FunctionDefContext) child;
                    compileFunction(functionDefContext);
                }
            }

            mark(mainLabel);

            for (int index = 0; index < programContext.getChildCount(); index++) {
                ParseTree child = programContext.getChild(index);
                if (child instanceof MandrillParser.StatementContext) {
                    MandrillParser.StatementContext statementContext = (MandrillParser.StatementContext) child;
                    compileStatement(statementContext, null);
                }
            }

            emit("jump", 0xFFFFFFFFL);
        }

        private void compileFunction(MandrillParser.FunctionDefContext functionContext) {
            FunctionSymbol functionSymbol = table.resolveFunction(functionContext.Identifier().getText());
            if (functionSymbol == null) {
                throw new IllegalStateException("Undefined function: " + functionContext.Identifier().getText());
            }

            mark(functionLabel(functionSymbol.getName()));

            for (VariableSymbol parameterSymbol : functionSymbol.getParameterOrder()) {
                emit("dlwrite", parameterSymbol.getIndex());
            }

            compileStmtBlock(functionContext.stmtBlock(), functionSymbol);
            emit("dstore", 0);
            emit("ret", 0);
        }

        private void compileStmtBlock(MandrillParser.StmtBlockContext blockContext, FunctionSymbol functionSymbol) {
            for (MandrillParser.StatementContext statementContext : blockContext.statement()) {
                compileStatement(statementContext, functionSymbol);
            }
        }

        private void compileStatement(MandrillParser.StatementContext statementContext, FunctionSymbol functionSymbol) {
            if (statementContext.assignStatement() != null) {
                compileAssignStatement(statementContext.assignStatement(), functionSymbol);
                return;
            }
            if (statementContext.loopStatement() != null) {
                compileLoopStatement(statementContext.loopStatement(), functionSymbol);
                return;
            }
            if (statementContext.conditionStatement() != null) {
                compileConditionStatement(statementContext.conditionStatement(), functionSymbol);
                return;
            }
            if (statementContext.jumpStmt() != null) {
                compileJumpStatement(statementContext.jumpStmt(), functionSymbol);
                return;
            }
            if (statementContext.declarationStmt() != null) {
                return;
            }
            if (statementContext.stmtBlock() != null) {
                compileStmtBlock(statementContext.stmtBlock(), functionSymbol);
            }
        }

        private void compileAssignStatement(MandrillParser.AssignStatementContext ctx, FunctionSymbol functionSymbol) {
            if (ctx.Identifier() != null && ctx.arraySuffix() != null) {
                ResolvedVariable target = resolveVariable(functionSymbol, ctx.Identifier().getText());
                ValueKind actualKind = compileExpression(ctx.rvalue().expression(), functionSymbol, ValueKind.ARRAY);
                if (actualKind == ValueKind.INT) {
                    emit("dstore", 4);
                    emit("eval", Constants.EVAL_MUL);
                    emit("malloc", 0);
                }
                emitStore(target);
                return;
            }

            if (ctx.lvalue() instanceof MandrillParser.PrintIntegerContext) {
                ValueKind kind = compileExpression(ctx.rvalue().expression(), functionSymbol, null);
                emit(kind == ValueKind.ARRAY ? "puts" : "puti", 0);
                return;
            }
            if (ctx.lvalue() instanceof MandrillParser.PrintCharContext) {
                compileExpression(ctx.rvalue().expression(), functionSymbol, ValueKind.INT);
                emit("putc", 0);
                return;
            }

            MandrillParser.TargetVariableContext targetContext = (MandrillParser.TargetVariableContext) ctx.lvalue();
            ResolvedVariable target = resolveVariable(functionSymbol, targetContext.Identifier().getText());
            if (targetContext.LeftBracket() != null) {
                compileExpression(ctx.rvalue().expression(), functionSymbol, ValueKind.INT);
                emitAddressForElement(target, targetContext.expression(), functionSymbol);
                emit("dawrite", 0);
                return;
            }

            ValueKind targetKind = target.symbol.getKind();
            ValueKind actualKind = compileExpression(ctx.rvalue().expression(), functionSymbol, targetKind);
            if (targetKind == ValueKind.ARRAY && actualKind == ValueKind.INT) {
                emit("dstore", 4);
                emit("eval", Constants.EVAL_MUL);
                emit("malloc", 0);
            }
            emitStore(target);
        }

        private void compileLoopStatement(MandrillParser.LoopStatementContext ctx, FunctionSymbol functionSymbol) {
            Label conditionLabel = newLabel("while_condition");
            Label bodyLabel = newLabel("while_body");
            Label exitLabel = newLabel("while_exit");

            loopStack.push(new LoopContext(conditionLabel, exitLabel));

            mark(conditionLabel);
            emit("dstore", exitLabel);
            emit("dstore", bodyLabel);
            compileExpression(ctx.expression(), functionSymbol, ValueKind.INT);
            emit("eval", Constants.EVAL_CONDITION);

            mark(bodyLabel);
            compileStmtBlock(ctx.stmtBlock(), functionSymbol);
            emitJump(conditionLabel);

            mark(exitLabel);
            loopStack.pop();
        }

        private void compileConditionStatement(MandrillParser.ConditionStatementContext ctx,
                FunctionSymbol functionSymbol) {
            Label thenLabel = newLabel("if_then");
            Label elseLabel = newLabel("if_else");
            Label endLabel = newLabel("if_end");

            emit("dstore", elseLabel);
            emit("dstore", thenLabel);
            compileExpression(ctx.expression(), functionSymbol, ValueKind.INT);
            emit("eval", Constants.EVAL_CONDITION);

            mark(thenLabel);
            compileStmtBlock(ctx.thenStatement, functionSymbol);
            emitJump(endLabel);

            mark(elseLabel);
            if (ctx.elseStatement != null) {
                compileStmtBlock(ctx.elseStatement, functionSymbol);
            }

            mark(endLabel);
        }

        private void compileJumpStatement(MandrillParser.JumpStmtContext ctx, FunctionSymbol functionSymbol) {
            if (ctx.Break() != null) {
                if (loopStack.isEmpty()) {
                    throw new IllegalStateException("break outside loop");
                }
                emitJump(loopStack.peek().breakLabel);
                return;
            }
            if (ctx.Continue() != null) {
                if (loopStack.isEmpty()) {
                    throw new IllegalStateException("continue outside loop");
                }
                emitJump(loopStack.peek().continueLabel);
                return;
            }

            ValueKind expectedKind = functionSymbol == null ? ValueKind.INT : functionSymbol.getReturnKind();
            compileExpression(ctx.expression(), functionSymbol, expectedKind);
            emit("ret", 0);
        }

        private ValueKind compileExpression(MandrillParser.ExpressionContext ctx, FunctionSymbol functionSymbol,
                ValueKind expectedKind) {
            if (ctx instanceof MandrillParser.IntLiteralContext) {
                MandrillParser.IntLiteralContext intLiteralContext = (MandrillParser.IntLiteralContext) ctx;
                emit("dstore", Long.parseLong(intLiteralContext.IntegerConstant().getText()));
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.CharLiteralContext) {
                MandrillParser.CharLiteralContext charLiteralContext = (MandrillParser.CharLiteralContext) ctx;
                emit("dstore", parseCharacterLiteral(charLiteralContext.CharacterConstant().getText()));
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.StringLiteralContext) {
                MandrillParser.StringLiteralContext stringLiteralContext = (MandrillParser.StringLiteralContext) ctx;
                emitStringLiteral(stringLiteralContext.StringConstant().getText());
                return ValueKind.ARRAY;
            }
            if (ctx instanceof MandrillParser.SourceVariableContext) {
                MandrillParser.SourceVariableContext sourceVariableContext = (MandrillParser.SourceVariableContext) ctx;
                ResolvedVariable variable = resolveVariable(functionSymbol,
                        sourceVariableContext.Identifier().getText());
                if (sourceVariableContext.LeftBracket() != null) {
                    emitLoad(variable);
                    compileExpression(sourceVariableContext.expression(), functionSymbol, ValueKind.INT);
                    emit("dstore", 4);
                    emit("eval", Constants.EVAL_MUL);
                    emit("eval", Constants.EVAL_ADD);
                    emit("daload", 0);
                    return ValueKind.INT;
                }
                emitLoad(variable);
                return variable.symbol.getKind();
            }
            if (ctx instanceof MandrillParser.FunctionCallContext) {
                MandrillParser.FunctionCallContext functionCallContext = (MandrillParser.FunctionCallContext) ctx;
                FunctionSymbol targetFunction = table.resolveFunction(functionCallContext.Identifier().getText());
                if (targetFunction == null) {
                    throw new IllegalStateException(
                            "Undefined function: " + functionCallContext.Identifier().getText());
                }
                List<MandrillParser.ExpressionContext> arguments = functionCallContext.argumentList() == null
                        ? List.of()
                        : functionCallContext.argumentList().expression();
                for (int index = arguments.size() - 1; index >= 0; index--) {
                    compileExpression(arguments.get(index), functionSymbol, null);
                }
                emit("dstore", targetFunction.getFrameSizeBytes());
                emit("jal", functionLabel(targetFunction.getName()));
                return targetFunction.getReturnKind();
            }
            if (ctx instanceof MandrillParser.InputIntContext) {
                if (expectedKind == ValueKind.ARRAY) {
                    emit("gets", 0);
                    return ValueKind.ARRAY;
                }
                emit("geti", 0);
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.InputChatContext) {
                emit("getc", 0);
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.SubgroupExpressionContext) {
                MandrillParser.SubgroupExpressionContext subgroupExpressionContext = (MandrillParser.SubgroupExpressionContext) ctx;
                return compileExpression(subgroupExpressionContext.expression(), functionSymbol, expectedKind);
            }
            if (ctx instanceof MandrillParser.MulDivModExpressionContext) {
                MandrillParser.MulDivModExpressionContext mulDivModExpressionContext = (MandrillParser.MulDivModExpressionContext) ctx;
                compileExpression(mulDivModExpressionContext.expression(0), functionSymbol, ValueKind.INT);
                compileExpression(mulDivModExpressionContext.expression(1), functionSymbol, ValueKind.INT);
                if (mulDivModExpressionContext.Star() != null) {
                    emit("eval", Constants.EVAL_MUL);
                } else if (mulDivModExpressionContext.Slash() != null) {
                    emit("eval", Constants.EVAL_DIV);
                } else {
                    emit("eval", Constants.EVAL_MOD);
                }
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.AddSubExpressionContext) {
                MandrillParser.AddSubExpressionContext addSubExpressionContext = (MandrillParser.AddSubExpressionContext) ctx;
                compileExpression(addSubExpressionContext.expression(0), functionSymbol, ValueKind.INT);
                compileExpression(addSubExpressionContext.expression(1), functionSymbol, ValueKind.INT);
                if (addSubExpressionContext.Plus() != null) {
                    emit("eval", Constants.EVAL_ADD);
                } else {
                    emit("eval", Constants.EVAL_MINUS);
                }
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.ComparingExpressionContext) {
                MandrillParser.ComparingExpressionContext comparingExpressionContext = (MandrillParser.ComparingExpressionContext) ctx;
                compileExpression(comparingExpressionContext.expression(0), functionSymbol, ValueKind.INT);
                compileExpression(comparingExpressionContext.expression(1), functionSymbol, ValueKind.INT);
                if (comparingExpressionContext.LessThan() != null) {
                    emit("eval", Constants.EVAL_LESS);
                } else if (comparingExpressionContext.LargeThan() != null) {
                    emit("eval", Constants.EVAL_GREATER);
                } else if (comparingExpressionContext.NoLessThan() != null) {
                    emit("eval", Constants.EVAL_GREATER_OR_EQUAL);
                } else {
                    emit("eval", Constants.EVAL_LESS_OR_EQUAL);
                }
                return ValueKind.INT;
            }
            if (ctx instanceof MandrillParser.EqualityExpressionContext) {
                MandrillParser.EqualityExpressionContext equalityExpressionContext = (MandrillParser.EqualityExpressionContext) ctx;
                compileExpression(equalityExpressionContext.expression(0), functionSymbol, ValueKind.INT);
                compileExpression(equalityExpressionContext.expression(1), functionSymbol, ValueKind.INT);
                if (equalityExpressionContext.Equality() != null) {
                    emit("eval", Constants.EVAL_EQUAL);
                } else {
                    emit("eval", Constants.EVAL_NOT_EQUAL);
                }
                return ValueKind.INT;
            }
            throw new IllegalStateException("Unsupported expression: " + ctx.getClass().getSimpleName());
        }

        private void emitStringLiteral(String tokenText) {
            String literal = decodeStringLiteral(tokenText);
            int[] codePoints = literal.codePoints().toArray();
            int bytes = (codePoints.length + 1) * 4;

            emit("dstore", bytes);
            emit("malloc", 0);
            emit("dwrite", table.getCompilerTempGlobalIndex());

            for (int index = 0; index < codePoints.length; index++) {
                emit("dstore", codePoints[index]);
                emit("dload", table.getCompilerTempGlobalIndex());
                emit("dstore", index * 4L);
                emit("eval", Constants.EVAL_ADD);
                emit("dawrite", 0);
            }

            emit("dload", table.getCompilerTempGlobalIndex());
        }

        private String decodeStringLiteral(String tokenText) {
            String content = tokenText.substring(1, tokenText.length() - 1);
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < content.length(); index++) {
                char ch = content.charAt(index);
                if (ch == '\\' && index + 1 < content.length()) {
                    builder.appendCodePoint(decodeEscape(content.charAt(++index)));
                } else {
                    builder.append(ch);
                }
            }
            return builder.toString();
        }

        private long parseCharacterLiteral(String tokenText) {
            String content = tokenText.substring(1, tokenText.length() - 1);
            if (content.startsWith("\\") && content.length() >= 2) {
                return decodeEscape(content.charAt(1));
            }
            return content.codePointAt(0);
        }

        private int decodeEscape(char escaped) {
            switch (escaped) {
                case 'n':
                    return '\n';
                case 'r':
                    return '\r';
                case 't':
                    return '\t';
                case 'b':
                    return '\b';
                case 'f':
                    return '\f';
                case '\\':
                    return '\\';
                case '\'':
                    return '\'';
                case '"':
                    return '"';
                default:
                    return escaped;
            }
        }

        private void emitLoad(ResolvedVariable variable) {
            if (variable.local) {
                emit("dlload", variable.symbol.getIndex());
            } else {
                emit("dload", variable.symbol.getIndex());
            }
        }

        private void emitStore(ResolvedVariable variable) {
            if (variable.local) {
                emit("dlwrite", variable.symbol.getIndex());
            } else {
                emit("dwrite", variable.symbol.getIndex());
            }
        }

        private void emitAddressForElement(ResolvedVariable variable, MandrillParser.ExpressionContext indexExpression,
                FunctionSymbol functionSymbol) {
            emitLoad(variable);
            compileExpression(indexExpression, functionSymbol, ValueKind.INT);
            emit("dstore", 4);
            emit("eval", Constants.EVAL_MUL);
            emit("eval", Constants.EVAL_ADD);
        }

        private ResolvedVariable resolveVariable(FunctionSymbol functionSymbol, String name) {
            if (functionSymbol != null) {
                VariableSymbol local = functionSymbol.getLocals().get(name);
                if (local != null) {
                    return new ResolvedVariable(local, true);
                }
                VariableSymbol parameter = functionSymbol.getParameters().get(name);
                if (parameter != null) {
                    return new ResolvedVariable(parameter, true);
                }
            }
            VariableSymbol global = table.resolveGlobal(name);
            if (global == null) {
                throw new IllegalStateException("Undefined variable: " + name);
            }
            return new ResolvedVariable(global, false);
        }

        private void emitJump(Label label) {
            emit("jump", label);
        }

        private void emit(String mnemonic, long operand) {
            instructions.add(new InstructionLine(mnemonic, operand, null));
        }

        private void emit(String mnemonic, Label label) {
            instructions.add(new InstructionLine(mnemonic, 0L, label));
        }

        private void mark(Label label) {
            label.address = instructions.size() * 8L;
        }

        private Label newLabel(String prefix) {
            return labels.computeIfAbsent(prefix + '_' + labelCounter++, Label::new);
        }

        private Label functionLabel(String functionName) {
            return labels.computeIfAbsent("func_" + functionName, Label::new);
        }

        private long resolveLabel(Label label) {
            if (label.address < 0) {
                throw new IllegalStateException("Unresolved label: " + label.name);
            }
            return label.address;
        }

        private static final class InstructionLine {
            private final String mnemonic;
            private final long operand;
            private final Label labelRef;

            private InstructionLine(String mnemonic, long operand, Label labelRef) {
                this.mnemonic = mnemonic;
                this.operand = operand;
                this.labelRef = labelRef;
            }
        }

        private static final class Label {
            private final String name;
            private long address = -1L;

            private Label(String name) {
                this.name = name;
            }
        }

        private static final class LoopContext {
            private final Label continueLabel;
            private final Label breakLabel;

            private LoopContext(Label continueLabel, Label breakLabel) {
                this.continueLabel = continueLabel;
                this.breakLabel = breakLabel;
            }
        }

        private static final class ResolvedVariable {
            private final VariableSymbol symbol;
            private final boolean local;

            private ResolvedVariable(VariableSymbol symbol, boolean local) {
                this.symbol = symbol;
                this.local = local;
            }
        }
    }
}