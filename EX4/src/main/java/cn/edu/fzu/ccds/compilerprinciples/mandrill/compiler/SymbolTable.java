package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {
    public enum SymbolKind {
        GLOBAL_VAR,
        LOCAL_VAR,
        PARAM,
        FUNCTION,
        ARRAY
    }

    public enum ValueType {
        INT,
        ARRAY_TYPE
    }

    public static class SymbolInfo {
        public final String name;
        public final SymbolKind kind;
        public ValueType valueType;
        public final int index;
        public boolean isArray;
        public int localOffset;

        public SymbolInfo(String name, SymbolKind kind, ValueType valueType, int index, boolean isArray) {
            this.name = name;
            this.kind = kind;
            this.valueType = valueType;
            this.index = index;
            this.isArray = isArray;
        }

        @Override
        public String toString() {
            return "SymbolInfo{" +
                    "name='" + name + '\'' +
                    ", kind=" + kind +
                    ", valueType=" + valueType +
                    ", index=" + index +
                    ", isArray=" + isArray +
                    ", localOffset=" + localOffset +
                    '}';
        }
    }

    private Map<String, SymbolInfo> globalSymbols;
    private Map<String, SymbolInfo> currentLocalSymbols;
    private Map<String, SymbolInfo> currentParamSymbols;
    private int globalVarCount;
    private int localVarCount;
    private int paramCount;
    private int functionCount;

    private Map<String, SymbolInfo> savedGlobalSymbols;
    private Map<String, SymbolInfo> savedParamSymbols;

    public SymbolTable() {
        this.globalSymbols = new HashMap<>();
        this.currentLocalSymbols = new HashMap<>();
        this.currentParamSymbols = new HashMap<>();
        this.globalVarCount = 0;
        this.localVarCount = 0;
        this.paramCount = 0;
        this.functionCount = 0;
        this.savedGlobalSymbols = new HashMap<>();
        this.savedParamSymbols = new HashMap<>();
    }

    public void enterScope() {
        currentLocalSymbols = new HashMap<>();
        currentParamSymbols = new HashMap<>();
        localVarCount = 0;
        paramCount = 0;
    }

    public void exitScope() {
        currentLocalSymbols = new HashMap<>();
        currentParamSymbols = new HashMap<>();
    }

    public void saveState() {
        savedGlobalSymbols = new HashMap<>(globalSymbols);
        savedParamSymbols = new HashMap<>(currentParamSymbols);
    }

    public void saveParamState() {
        for (SymbolInfo info : currentParamSymbols.values()) {
            savedParamSymbols.put(info.name, info);
        }
    }

    public void reinitializeForSemanticCheck() {
        globalSymbols = new HashMap<>(savedGlobalSymbols);
        currentParamSymbols = new HashMap<>(savedParamSymbols);
        currentLocalSymbols = new HashMap<>();
        globalVarCount = 0;
        for (SymbolInfo info : globalSymbols.values()) {
            if (info.kind == SymbolKind.GLOBAL_VAR) {
                globalVarCount++;
            }
        }
        functionCount = 0;
        for (SymbolInfo info : globalSymbols.values()) {
            if (info.kind == SymbolKind.FUNCTION) {
                functionCount++;
            }
        }
    }

    public SymbolInfo addGlobalVariable(String name, boolean isArray) {
        SymbolInfo info = new SymbolInfo(name, SymbolKind.GLOBAL_VAR,
                isArray ? ValueType.ARRAY_TYPE : ValueType.INT, globalVarCount++, isArray);
        globalSymbols.put(name, info);
        return info;
    }

    public SymbolInfo addLocalVariable(String name, boolean isArray) {
        SymbolInfo info = new SymbolInfo(name, SymbolKind.LOCAL_VAR,
                isArray ? ValueType.ARRAY_TYPE : ValueType.INT, localVarCount++, isArray);
        // Local offsets start after parameters: paramCount + localIndex
        info.localOffset = paramCount + (localVarCount - 1);
        currentLocalSymbols.put(name, info);
        return info;
    }

    public SymbolInfo addParameter(String name, boolean isArray) {
        SymbolInfo info = new SymbolInfo(name, SymbolKind.PARAM,
                isArray ? ValueType.ARRAY_TYPE : ValueType.INT, paramCount, isArray);
        info.localOffset = paramCount;
        paramCount++;
        currentParamSymbols.put(name, info);
        return info;
    }

    public SymbolInfo addFunction(String name, boolean returnsArray) {
        SymbolInfo info = new SymbolInfo(name, SymbolKind.FUNCTION,
                returnsArray ? ValueType.ARRAY_TYPE : ValueType.INT, functionCount++, returnsArray);
        globalSymbols.put(name, info);
        return info;
    }

    public SymbolInfo lookup(String name) {
        SymbolInfo info = currentParamSymbols.get(name);
        if (info != null)
            return info;
        info = currentLocalSymbols.get(name);
        if (info != null)
            return info;
        return globalSymbols.get(name);
    }

    public SymbolInfo lookupGlobal(String name) {
        return globalSymbols.get(name);
    }

    public SymbolInfo lookupLocal(String name) {
        SymbolInfo info = currentLocalSymbols.get(name);
        if (info != null)
            return info;
        return currentParamSymbols.get(name);
    }

    public Map<String, SymbolInfo> getGlobalSymbols() {
        return globalSymbols;
    }

    public Map<String, SymbolInfo> getCurrentLocalSymbols() {
        return currentLocalSymbols;
    }

    public Map<String, SymbolInfo> getCurrentParamSymbols() {
        return currentParamSymbols;
    }

    public int getLocalVarCount() {
        return localVarCount;
    }

    public int getParamCount() {
        return paramCount;
    }

    public int getGlobalVarCount() {
        return globalVarCount;
    }

    public int getFunctionCount() {
        return functionCount;
    }
}
