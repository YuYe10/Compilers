package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker.SymbolTable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static cn.edu.fzu.ccds.compilerprinciples.mandrill.simulator.Constants.*;

public class CompilerImpl implements Compiler {

    private final List<String> instructions = new ArrayList<>();
    private final Map<String, Integer> globalVariables = new HashMap<>();
    private final Map<String, Integer> functionAddresses = new HashMap<>();
    private final Map<String, Integer> stringLiterals = new HashMap<>();
    private final List<String> literalStrings = new ArrayList<>();
    private int globalVarIndex = 0;
    private int labelCounter = 0;
    private Deque<Integer> loopStartAddresses = new ArrayDeque<>();
    private Deque<Integer> loopEndAddresses = new ArrayDeque<>();

    @Override
    public String compile(InputStream inputStream) throws IOException {
        CompileContext context = frontend(inputStream);
        SymbolTable symbolTable = context.table();
        
        instructions.clear();
        globalVariables.clear();
        functionAddresses.clear();
        stringLiterals.clear();
        literalStrings.clear();
        globalVarIndex = 0;
        labelCounter = 0;
        loopStartAddresses.clear();
        loopEndAddresses.clear();

        MandrillParser.ProgramContext program = context.tree();
        
        for (MandrillParser.FunctionDefContext func : program.functionDef()) {
            String funcName = func.Identifier().getText();
            functionAddresses.put(funcName, instructions.size() * 8);
        }

        for (MandrillParser.FunctionDefContext func : program.functionDef()) {
            compileFunction(func, symbolTable);
        }

        for (MandrillParser.StatementContext stmt : program.statement()) {
            compileStatement(stmt, symbolTable, new ArrayList<>());
        }

        instructions.add("jump 0xFFFFFFFF");

        for (int i = 0; i < literalStrings.size(); i++) {
            String str = literalStrings.get(i);
            int addr = instructions.size() * 8;
            stringLiterals.put(str, addr);
        }

        StringBuilder sb = new StringBuilder();
        for (String instr : instructions) {
            sb.append(instr).append("\n");
        }

        for (String str : literalStrings) {
            for (char c : str.toCharArray()) {
                sb.append("dstore ").append((int) c).append("\n");
            }
            sb.append("dstore 0\n");
        }

        return sb.toString();
    }

    private void compileFunction(MandrillParser.FunctionDefContext func, SymbolTable symbolTable) {
        String funcName = func.Identifier().getText();
        symbolTable.enterFunction();

        List<String> localVars = new ArrayList<>();
        if (func.parameterList() != null) {
            for (MandrillParser.ParameterContext param : func.parameterList().parameter()) {
                String paramName = param.Identifier().getText();
                localVars.add(paramName);
            }
        }

        visitStmtBlock(func.stmtBlock(), symbolTable, localVars);

        symbolTable.exitFunction();
    }

    private void compileStatement(MandrillParser.StatementContext stmt, SymbolTable symbolTable, List<String> localVars) {
        visitStatement(stmt, symbolTable, localVars);
    }

    private void visitStatement(MandrillParser.StatementContext stmt, SymbolTable symbolTable, List<String> localVars) {
        if (stmt instanceof MandrillParser.AssignStatementContext) {
            visitAssignStatement((MandrillParser.AssignStatementContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.LoopStatementContext) {
            visitLoopStatement((MandrillParser.LoopStatementContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.ConditionStatementContext) {
            visitConditionStatement((MandrillParser.ConditionStatementContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.JumpStmtContext) {
            visitJumpStmt((MandrillParser.JumpStmtContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.DeclarationStmtContext) {
            visitDeclarationStmt((MandrillParser.DeclarationStmtContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.StmtBlockContext) {
            visitStmtBlock((MandrillParser.StmtBlockContext) stmt, symbolTable, localVars);
        } else if (stmt instanceof MandrillParser.EmptyStmtContext) {
        }
    }

    private void visitAssignStatement(MandrillParser.AssignStatementContext ctx, SymbolTable symbolTable, List<String> localVars) {
        if (ctx.lvalue() != null) {
            MandrillParser.LvalueContext lvalue = ctx.lvalue();
            
            if (lvalue instanceof MandrillParser.PrintIntegerContext) {
                visitExpression(ctx.rvalue().expression(), symbolTable, localVars);
                instructions.add("puti 0");
            } else if (lvalue instanceof MandrillParser.PrintCharContext) {
                visitExpression(ctx.rvalue().expression(), symbolTable, localVars);
                instructions.add("putc 0");
            } else if (lvalue instanceof MandrillParser.TargetVariableContext) {
                MandrillParser.TargetVariableContext target = (MandrillParser.TargetVariableContext) lvalue;
                String varName = target.Identifier().getText();
                
                if (target.LeftBracket() != null) {
                    visitExpression(target.expression(), symbolTable, localVars);
                    int varIndex = getVariableIndex(varName, symbolTable, localVars);
                    if (isLocal(varName, localVars)) {
                        instructions.add("dlload " + varIndex);
                    } else {
                        instructions.add("dload " + varIndex);
                    }
                    instructions.add("eval " + EVAL_ADD);
                    visitExpression(ctx.rvalue().expression(), symbolTable, localVars);
                    instructions.add("dawrite 0");
                } else {
                    visitExpression(ctx.rvalue().expression(), symbolTable, localVars);
                    int varIndex = getVariableIndex(varName, symbolTable, localVars);
                    if (isLocal(varName, localVars)) {
                        instructions.add("dlwrite " + varIndex);
                    } else {
                        instructions.add("dwrite " + varIndex);
                    }
                }
            }
        } else if (ctx.Identifier() != null && ctx.arraySuffix() != null) {
            String varName = ctx.Identifier().getText();
            visitExpression(ctx.rvalue().expression(), symbolTable, localVars);
            instructions.add("malloc 0");
            int varIndex = getVariableIndex(varName, symbolTable, localVars);
            if (isLocal(varName, localVars)) {
                instructions.add("dlwrite " + varIndex);
            } else {
                instructions.add("dwrite " + varIndex);
            }
        }
    }

    private void visitLoopStatement(MandrillParser.LoopStatementContext ctx, SymbolTable symbolTable, List<String> localVars) {
        int loopStart = instructions.size() * 8;
        loopStartAddresses.push(loopStart);
        
        visitExpression(ctx.expr, symbolTable, localVars);
        
        int afterLoop = (instructions.size() + 2) * 8;
        loopEndAddresses.push(afterLoop);
        
        instructions.add("dstore " + afterLoop);
        instructions.add("dstore " + loopStart);
        instructions.add("eval " + EVAL_CONDITION);
        
        visitStmtBlock(ctx.stmtBlock(), symbolTable, localVars);
        
        instructions.add("jump " + loopStart);
        
        loopStartAddresses.pop();
        loopEndAddresses.pop();
    }

    private void visitConditionStatement(MandrillParser.ConditionStatementContext ctx, SymbolTable symbolTable, List<String> localVars) {
        visitExpression(ctx.expr, symbolTable, localVars);
        
        int elseStart = (instructions.size() + 2) * 8;
        int afterIf = (instructions.size() + 3) * 8;
        
        if (ctx.elseStatement != null) {
            instructions.add("dstore " + elseStart);
            instructions.add("dstore " + afterIf);
            instructions.add("eval " + EVAL_CONDITION);
            
            visitStmtBlock(ctx.thenStatement, symbolTable, localVars);
            
            instructions.add("jump " + afterIf);
            
            visitStmtBlock(ctx.elseStatement, symbolTable, localVars);
        } else {
            instructions.add("dstore " + afterIf);
            instructions.add("dstore " + afterIf);
            instructions.add("eval " + EVAL_CONDITION);
            
            visitStmtBlock(ctx.thenStatement, symbolTable, localVars);
        }
    }

    private void visitJumpStmt(MandrillParser.JumpStmtContext ctx, SymbolTable symbolTable, List<String> localVars) {
        if (ctx.Break() != null) {
            int loopEnd = loopEndAddresses.isEmpty() ? 0xFFFFFFFF : loopEndAddresses.peek();
            instructions.add("jump " + loopEnd);
        } else if (ctx.Continue() != null) {
            int loopStart = loopStartAddresses.isEmpty() ? 0 : loopStartAddresses.peek();
            instructions.add("jump " + loopStart);
        } else if (ctx.Return() != null) {
            if (ctx.expression() != null) {
                visitExpression(ctx.expression(), symbolTable, localVars);
            }
            instructions.add("ret 0");
        }
    }

    private void visitDeclarationStmt(MandrillParser.DeclarationStmtContext ctx, SymbolTable symbolTable, List<String> localVars) {
        String varName = ctx.Identifier().getText();
        if (!localVars.contains(varName)) {
            localVars.add(varName);
        }
    }

    private void visitStmtBlock(MandrillParser.StmtBlockContext ctx, SymbolTable symbolTable, List<String> localVars) {
        for (MandrillParser.StatementContext stmt : ctx.statement()) {
            visitStatement(stmt, symbolTable, localVars);
        }
    }

    private void visitExpression(MandrillParser.ExpressionContext expr, SymbolTable symbolTable, List<String> localVars) {
        if (expr instanceof MandrillParser.IntLiteralContext) {
            instructions.add("dstore " + ((MandrillParser.IntLiteralContext) expr).IntegerConstant().getText());
        } else if (expr instanceof MandrillParser.CharLiteralContext) {
            String text = ((MandrillParser.CharLiteralContext) expr).CharacterConstant().getText();
            char c = text.charAt(1);
            if (c == '\\' && text.length() > 2) {
                char next = text.charAt(2);
                switch (next) {
                    case 'n' -> c = '\n';
                    case 't' -> c = '\t';
                    case 'r' -> c = '\r';
                    case '\\' -> c = '\\';
                    case '\'' -> c = '\'';
                    default -> c = next;
                }
            }
            instructions.add("dstore " + (int) c);
        } else if (expr instanceof MandrillParser.StringLiteralContext) {
            String str = ((MandrillParser.StringLiteralContext) expr).StringConstant().getText();
            str = str.substring(1, str.length() - 1);
            StringBuilder decoded = new StringBuilder();
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '\\' && i + 1 < str.length()) {
                    i++;
                    switch (str.charAt(i)) {
                        case 'n' -> decoded.append('\n');
                        case 't' -> decoded.append('\t');
                        case 'r' -> decoded.append('\r');
                        case '\\' -> decoded.append('\\');
                        case '"' -> decoded.append('"');
                        default -> decoded.append(str.charAt(i));
                    }
                } else {
                    decoded.append(str.charAt(i));
                }
            }
            str = decoded.toString();
            if (!stringLiterals.containsKey(str)) {
                literalStrings.add(str);
                stringLiterals.put(str, 0);
            }
        } else if (expr instanceof MandrillParser.SourceVariableContext) {
            MandrillParser.SourceVariableContext varCtx = (MandrillParser.SourceVariableContext) expr;
            String varName = varCtx.Identifier().getText();
            
            if (varCtx.LeftBracket() != null) {
                visitExpression(varCtx.expression(), symbolTable, localVars);
                int varIndex = getVariableIndex(varName, symbolTable, localVars);
                if (isLocal(varName, localVars)) {
                    instructions.add("dlload " + varIndex);
                } else {
                    instructions.add("dload " + varIndex);
                }
                instructions.add("eval " + EVAL_ADD);
                instructions.add("daload 0");
            } else {
                int varIndex = getVariableIndex(varName, symbolTable, localVars);
                if (isLocal(varName, localVars)) {
                    instructions.add("dlload " + varIndex);
                } else {
                    instructions.add("dload " + varIndex);
                }
            }
        } else if (expr instanceof MandrillParser.FunctionCallContext) {
            MandrillParser.FunctionCallContext funcCall = (MandrillParser.FunctionCallContext) expr;
            String funcName = funcCall.Identifier().getText();
            
            List<MandrillParser.ExpressionContext> args = new ArrayList<>();
            if (funcCall.argumentList() != null) {
                args.addAll(funcCall.argumentList().expression());
            }
            
            for (MandrillParser.ExpressionContext arg : args) {
                visitExpression(arg, symbolTable, localVars);
            }
            
            instructions.add("dstore 0");
            instructions.add("jal " + functionAddresses.get(funcName));
        } else if (expr instanceof MandrillParser.InputIntContext) {
            instructions.add("geti 0");
        } else if (expr instanceof MandrillParser.InputChatContext) {
            instructions.add("getc 0");
        } else if (expr instanceof MandrillParser.SubgroupExpressionContext) {
            visitExpression(((MandrillParser.SubgroupExpressionContext) expr).expression(), symbolTable, localVars);
        } else if (expr instanceof MandrillParser.MulDivModExpressionContext) {
            MandrillParser.MulDivModExpressionContext mulDiv = (MandrillParser.MulDivModExpressionContext) expr;
            visitExpression(mulDiv.expression(0), symbolTable, localVars);
            visitExpression(mulDiv.expression(1), symbolTable, localVars);
            int op = switch (mulDiv.op.getText()) {
                case "*" -> EVAL_MUL;
                case "/" -> EVAL_DIV;
                case "%" -> EVAL_MOD;
                default -> EVAL_ADD;
            };
            instructions.add("eval " + op);
        } else if (expr instanceof MandrillParser.AddSubExpressionContext) {
            MandrillParser.AddSubExpressionContext addSub = (MandrillParser.AddSubExpressionContext) expr;
            visitExpression(addSub.expression(0), symbolTable, localVars);
            visitExpression(addSub.expression(1), symbol