package cn.edu.fzu.ccds.compilerprinciples.mandrill.compiler;

public abstract class Symbol {
    protected final String name;
    protected final Scope scope;
    protected boolean isArray;

    public enum Scope {
        GLOBAL, LOCAL, PARAMETER
    }

    public Symbol(String name, Scope scope, boolean isArray) {
        this.name = name;
        this.scope = scope;
        this.isArray = isArray;
    }

    public String getName() {
        return name;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isArray() {
        return isArray;
    }

    public void setArray(boolean array) {
        isArray = array;
    }
}