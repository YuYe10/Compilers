package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import java.util.*;

public class SymbolTable {
    private Map<String, Symbol> globalSymbols = new HashMap<>();
    private Deque<Map<String, Symbol>> localScopes = new ArrayDeque<>();
    private FunctionSymbol currentFunction = null;
    private int globalIndex = 0;

    public void enterScope() {
        localScopes.push(new HashMap<>());
    }

    public void exitScope() {
        localScopes.pop();
    }

    public void setCurrentFunction(FunctionSymbol func) {
        this.currentFunction = func;
    }

    public FunctionSymbol getCurrentFunction() {
        return currentFunction;
    }

    public Symbol lookup(String name) {
        for (Map<String, Symbol> scope : localScopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return globalSymbols.get(name);
    }

    public void addGlobal(Symbol symbol) {
        if (globalSymbols.containsKey(symbol.getName())) {
            throw new SemanticError("Duplicate global symbol: " + symbol.getName());
        }
        if (symbol instanceof VariableSymbol) {
            ((VariableSymbol) symbol).setOffset(globalIndex++);
        }
        globalSymbols.put(symbol.getName(), symbol);
    }

    public void addLocal(Symbol symbol) {
        if (localScopes.isEmpty()) {
            throw new SemanticError("Cannot add local symbol outside function scope");
        }
        Map<String, Symbol> currentScope = localScopes.peek();
        if (currentScope.containsKey(symbol.getName())) {
            throw new SemanticError("Duplicate local symbol: " + symbol.getName());
        }
        currentScope.put(symbol.getName(), symbol);

        if (symbol instanceof VariableSymbol && currentFunction != null) {
            VariableSymbol var = (VariableSymbol) symbol;
            if (var.getScope() == Symbol.Scope.LOCAL) {
                var.setOffset(currentFunction.getParamCount() + currentFunction.getLocalCount());
                currentFunction.setLocalCount(currentFunction.getLocalCount() + 1);
            }
        }
    }

    public void addParameter(VariableSymbol param) {
        if (currentFunction == null) {
            throw new SemanticError("Cannot add parameter outside function");
        }
        param.setOffset(currentFunction.getParamCount());
        currentFunction.addParameter(param);
        if (!localScopes.isEmpty()) {
            localScopes.peek().put(param.getName(), param);
        }
    }

    public void restoreParameter(VariableSymbol param) {
        if (!localScopes.isEmpty()) {
            localScopes.peek().put(param.getName(), param);
        }
    }

    public FunctionSymbol getFunction(String name) {
        Symbol symbol = globalSymbols.get(name);
        if (symbol instanceof FunctionSymbol) {
            return (FunctionSymbol) symbol;
        }
        return null;
    }

    public Collection<FunctionSymbol> getFunctions() {
        List<FunctionSymbol> funcs = new ArrayList<>();
        for (Symbol symbol : globalSymbols.values()) {
            if (symbol instanceof FunctionSymbol) {
                funcs.add((FunctionSymbol) symbol);
            }
        }
        return funcs;
    }

    public int getGlobalCount() {
        int count = 0;
        for (Symbol symbol : globalSymbols.values()) {
            if (symbol instanceof VariableSymbol) {
                count++;
            }
        }
        return count;
    }

    public static class SemanticError extends RuntimeException {
        public SemanticError(String message) {
            super(message);
        }
    }
}