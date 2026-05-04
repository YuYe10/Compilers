package cn.edu.fzu.ccds.compilerprinciples.mandrill.parser;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.Token;

import java.util.List;

public class HandcraftParser implements Parser {
    private final List<Token> tokens;
    private boolean hasError = false;

    public HandcraftParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean parse() {
        return !hasError;
    }
}
