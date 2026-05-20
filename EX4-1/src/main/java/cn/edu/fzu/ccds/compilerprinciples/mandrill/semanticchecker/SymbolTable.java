package cn.edu.fzu.ccds.compilerprinciples.mandrill.semanticchecker;

import java.util.HashMap;
import java.util.Stack;

public class SymbolTable {
    public enum MandrillType {
        INT, ARRAY_PTR, UNKNOWN
    }

    public enum ScopeType {
        GLOBAL, LOCAL
    }

    public static class Symbol {
        public final String name;
        public MandrillType type;
        public final ScopeType scope;
        public final int scopeLevel;

        public Symbol(String name, MandrillType type, ScopeType scope, int scopeLevel) {
            this.name = name;
            this.type = type;
            this.scope = scope;
            this.scopeLevel = scopeLevel;
        }
    }

    private static class Scope {
        public final ScopeType type;
        public final int level;
        public final HashMap<String, Symbol> symbols;

        public Scope(ScopeType type, int level) {
            this.type = type;
            this.level = level;
            this.symbols = new HashMap<>();
        }
    }

    private final Stack<Scope> scopeStack;
    private final HashMap<String, Symbol> globalSymbols;
    private final HashMap<String, Symbol> allSymbols;
    private int scopeLevel;
    private int functionNestingLevel;

    public SymbolTable() {
        this.scopeStack = new Stack<>();
        this.globalSymbols = new HashMap<>();
        this.allSymbols = new HashMap<>();
        this.scopeLevel = 0;
        this.functionNestingLevel = 0;
    }

    public void enterFunction() {
        functionNestingLevel++;
        scopeLevel++;
        scopeStack.push(new Scope(ScopeType.LOCAL, scopeLevel));
    }

    public void exitFunction() {
        if (!scopeStack.isEmpty()) {
            scopeStack.pop();
            scopeLevel--;
            functionNestingLevel--;
        }
    }

    public void enterBlock() {
        if (functionNestingLevel > 0) {
            scopeLevel++;
            scopeStack.push(new Scope(ScopeType.LOCAL, scopeLevel));
        }
    }

    public void exitBlock() {
        if (!scopeStack.isEmpty() && functionNestingLevel > 0) {
            scopeStack.pop();
            scopeLevel--;
        }
    }

    public void declareSymbol(String name, MandrillType type, ScopeType scopeType) {
        Symbol symbol = new Symbol(name, type, scopeType, scopeLevel);

        if (scopeType == ScopeType.GLOBAL) {
            globalSymbols.put(name, symbol);
        } else {
            if (!scopeStack.isEmpty()) {
                scopeStack.peek().symbols.put(name, symbol);
            } else {
                globalSymbols.put(name, symbol);
            }
        }
        allSymbols.put(name + "@" + scopeLevel, symbol);
    }

    public void updateSymbolType(String name, MandrillType newType) {
        Symbol symbol = lookup(name);
        if (symbol != null) {
            if (symbol.type == MandrillType.UNKNOWN) {
                symbol.type = newType;
            }
        }
    }

    public Symbol lookup(String name) {
        for (int i = scopeStack.size() - 1; i >= 0; i--) {
            Symbol symbol = scopeStack.get(i).symbols.get(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return globalSymbols.get(name);
    }

    public Symbol lookupGlobal(String name) {
        return globalSymbols.get(name);
    }

    public boolean isDeclared(String name) {
        if (lookup(name) != null) {
            return true;
        }
        for (String key : allSymbols.keySet()) {
            if (key.startsWith(name + "@")) {
                return true;
            }
        }
        return false;
    }

    public MandrillType getType(String name) {
        Symbol symbol = lookup(name);
        if (symbol != null) {
            return symbol.type;
        }
        for (String key : allSymbols.keySet()) {
            if (key.startsWith(name + "@")) {
                return allSymbols.get(key).type;
            }
        }
        return MandrillType.UNKNOWN;
    }

    public int getScopeLevel() {
        return scopeLevel;
    }

    public int getFunctionNestingLevel() {
        return functionNestingLevel;
    }
}
