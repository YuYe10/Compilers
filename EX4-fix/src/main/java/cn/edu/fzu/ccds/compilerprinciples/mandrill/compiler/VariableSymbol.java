package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

public class VariableSymbol extends Symbol {
    private int offset;

    public VariableSymbol(String name, Scope scope, boolean isArray) {
        super(name, scope, isArray);
        this.offset = -1;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}