package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.antlr.MandrillParser;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SymbolTable {

    public enum ValueKind {
        INT,
        ARRAY
    }

    public static final class VariableSymbol {
        private final String name;
        private final ValueKind kind;
        private final int index;

        private VariableSymbol(String name, ValueKind kind, int index) {
            this.name = name;
            this.kind = kind;
            this.index = index;
        }

        public String getName() {
            return name;
        }

        public ValueKind getKind() {
            return kind;
        }

        public int getIndex() {
            return index;
        }
    }

    public static final class FunctionSymbol {
        private final String name;
        private final ValueKind returnKind;
        private final MandrillParser.FunctionDefContext context;
        private final LinkedHashMap<String, VariableSymbol> parameters = new LinkedHashMap<>();
        private final List<VariableSymbol> parameterOrder = new ArrayList<>();
        private final Map<ParseTree, VariableSymbol> declaredVariables = new LinkedHashMap<>();
        private int nextLocalIndex = 0;
        private int frameSizeBytes = 4;

        private FunctionSymbol(String name, ValueKind returnKind, MandrillParser.FunctionDefContext context) {
            this.name = name;
            this.returnKind = returnKind;
            this.context = context;
        }

        public String getName() {
            return name;
        }

        public ValueKind getReturnKind() {
            return returnKind;
        }

        public MandrillParser.FunctionDefContext getContext() {
            return context;
        }

        public Map<String, VariableSymbol> getParameters() {
            return parameters;
        }

        public List<VariableSymbol> getParameterOrder() {
            return parameterOrder;
        }

        public VariableSymbol getDeclaredVariable(ParseTree declarationContext) {
            return declaredVariables.get(declarationContext);
        }

        public int getFrameSizeBytes() {
            return frameSizeBytes;
        }

        private VariableSymbol defineParameter(String variableName, ValueKind kind) {
            if (parameters.containsKey(variableName)) {
                throw new IllegalStateException("Duplicate parameter: " + variableName + " in function " + name);
            }
            VariableSymbol symbol = new VariableSymbol(variableName, kind, nextLocalIndex++);
            parameters.put(variableName, symbol);
            parameterOrder.add(symbol);
            updateFrameSize();
            return symbol;
        }

        private VariableSymbol defineLocal(String variableName, ValueKind kind) {
            VariableSymbol symbol = new VariableSymbol(variableName, kind, nextLocalIndex++);
            updateFrameSize();
            return symbol;
        }

        private void registerDeclaredVariable(ParseTree declarationContext, VariableSymbol symbol) {
            declaredVariables.put(declarationContext, symbol);
        }

        private void updateFrameSize() {
            frameSizeBytes = Math.max(4, nextLocalIndex * 4);
        }
    }

    private final LinkedHashMap<String, VariableSymbol> globals = new LinkedHashMap<>();
    private final LinkedHashMap<String, FunctionSymbol> functions = new LinkedHashMap<>();
    private int nextGlobalIndex = 0;
    private int compilerTempGlobalIndex = -1;

    public VariableSymbol defineGlobal(String name, ValueKind kind) {
        VariableSymbol existing = globals.get(name);
        if (existing != null) {
            if (existing.kind == kind) {
                return existing;
            }
            // allow implicit upgrade from INT to ARRAY when first usage was scalar
            if (existing.kind == ValueKind.INT && kind == ValueKind.ARRAY) {
                VariableSymbol upgraded = new VariableSymbol(name, kind, existing.index);
                globals.put(name, upgraded);
                return upgraded;
            }
            // if already an ARRAY and later used as INT, keep ARRAY (no change)
            if (existing.kind == ValueKind.ARRAY && kind == ValueKind.INT) {
                return existing;
            }
            throw new IllegalStateException("Conflicting global kinds for " + name);
        }
        VariableSymbol symbol = new VariableSymbol(name, kind, nextGlobalIndex++);
        globals.put(name, symbol);
        return symbol;
    }

    public VariableSymbol resolveGlobal(String name) {
        return globals.get(name);
    }

    public FunctionSymbol defineFunction(String name, ValueKind returnKind, MandrillParser.FunctionDefContext context) {
        FunctionSymbol existing = functions.get(name);
        if (existing != null) {
            if (existing.returnKind != returnKind) {
                throw new IllegalStateException("Conflicting function kinds for " + name);
            }
            return existing;
        }
        FunctionSymbol symbol = new FunctionSymbol(name, returnKind, context);
        functions.put(name, symbol);
        return symbol;
    }

    public FunctionSymbol resolveFunction(String name) {
        return functions.get(name);
    }

    public Map<String, VariableSymbol> getGlobals() {
        return globals;
    }

    public Map<String, FunctionSymbol> getFunctions() {
        return functions;
    }

    public int getCompilerTempGlobalIndex() {
        if (compilerTempGlobalIndex < 0) {
            throw new IllegalStateException("Compiler temp global was not reserved");
        }
        return compilerTempGlobalIndex;
    }

    public void finalizeLayout() {
        if (compilerTempGlobalIndex < 0) {
            compilerTempGlobalIndex = nextGlobalIndex++;
        }
    }

    VariableSymbol defineParameter(FunctionSymbol functionSymbol, String name, ValueKind kind) {
        return functionSymbol.defineParameter(name, kind);
    }

    VariableSymbol defineLocal(FunctionSymbol functionSymbol, String name, ValueKind kind) {
        return functionSymbol.defineLocal(name, kind);
    }

    void registerDeclaredVariable(FunctionSymbol functionSymbol, ParseTree declarationContext, VariableSymbol symbol) {
        functionSymbol.registerDeclaredVariable(declarationContext, symbol);
    }
}