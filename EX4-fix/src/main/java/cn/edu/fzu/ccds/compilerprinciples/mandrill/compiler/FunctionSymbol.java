package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

import java.util.ArrayList;
import java.util.List;

public class FunctionSymbol extends Symbol {
    private List<VariableSymbol> parameters;
    private boolean isArrayReturn;
    private int localCount;
    private long address;

    public FunctionSymbol(String name, boolean isArrayReturn) {
        super(name, Scope.GLOBAL, isArrayReturn);
        this.parameters = new ArrayList<>();
        this.localCount = 0;
        this.address = -1;
    }

    public List<VariableSymbol> getParameters() {
        return parameters;
    }

    public void addParameter(VariableSymbol param) {
        parameters.add(param);
    }

    public boolean isArrayReturn() {
        return isArrayReturn;
    }

    public void setArrayReturn(boolean arrayReturn) {
        isArrayReturn = arrayReturn;
    }

    public int getLocalCount() {
        return localCount;
    }

    public void setLocalCount(int localCount) {
        this.localCount = localCount;
    }

    public long getAddress() {
        return address;
    }

    public void setAddress(long address) {
        this.address = address;
    }

    public int getParamCount() {
        return parameters.size();
    }
}