package cn.edu.fzu.ccds.compilerprinciples.mandrill.parser;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.Token;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.TokenType;

import java.util.List;

public class HandcraftParser implements Parser {
    private final List<Token> tokens;
    private int current = 0;
    private boolean hasError = false;

    public HandcraftParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean parse() {
        while (!isAtEnd()) {
            if (check(TokenType.FUNC)) {
                functionDef();
            } else {
                statement();
            }
        }
        return !hasError;
    }

    private void functionDef() {
        consume(TokenType.FUNC);
        if (check(TokenType.LBRACKET)) {
            consume(TokenType.LBRACKET);
            consume(TokenType.RBRACKET);
        }
        consume(TokenType.IDENTIFIER);
        consume(TokenType.LPAREN);
        if (!check(TokenType.RPAREN)) {
            parameterList();
        }
        consume(TokenType.RPAREN);
        block();
    }

    private void parameterList() {
        consume(TokenType.IDENTIFIER);
        if (check(TokenType.LBRACKET)) {
            consume(TokenType.LBRACKET);
            consume(TokenType.RBRACKET);
        }
        while (check(TokenType.COMMA)) {
            consume(TokenType.COMMA);
            consume(TokenType.IDENTIFIER);
            if (check(TokenType.LBRACKET)) {
                consume(TokenType.LBRACKET);
                consume(TokenType.RBRACKET);
            }
        }
    }

    private void statement() {
        if (check(TokenType.IF)) {
            ifStmt();
        } else if (check(TokenType.WHILE)) {
            whileStmt();
        } else if (check(TokenType.BREAK)) {
            jumpStmt();
        } else if (check(TokenType.CONTINUE)) {
            jumpStmt();
        } else if (check(TokenType.RETURN)) {
            jumpStmt();
        } else if (check(TokenType.LOCAL) || check(TokenType.GLOBAL)) {
            declarationStmt();
        } else if (check(TokenType.LBRACE)) {
            block();
        } else if (check(TokenType.SEMI)) {
            consume(TokenType.SEMI);
        } else {
            assignmentStmt();
        }
    }

    private void block() {
        consume(TokenType.LBRACE);
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statement();
        }
        consume(TokenType.RBRACE);
    }

    private void ifStmt() {
        consume(TokenType.IF);
        consume(TokenType.LPAREN);
        expression();
        consume(TokenType.RPAREN);
        block();
        if (check(TokenType.ELSE)) {
            consume(TokenType.ELSE);
            block();
        }
    }

    private void whileStmt() {
        consume(TokenType.WHILE);
        consume(TokenType.LPAREN);
        expression();
        consume(TokenType.RPAREN);
        block();
    }

    private void jumpStmt() {
        if (check(TokenType.BREAK)) {
            consume(TokenType.BREAK);
            consume(TokenType.SEMI);
        } else if (check(TokenType.CONTINUE)) {
            consume(TokenType.CONTINUE);
            consume(TokenType.SEMI);
        } else if (check(TokenType.RETURN)) {
            consume(TokenType.RETURN);
            expression();
            consume(TokenType.SEMI);
        }
    }

    private void declarationStmt() {
        if (check(TokenType.LOCAL)) {
            consume(TokenType.LOCAL);
        } else {
            consume(TokenType.GLOBAL);
        }
        consume(TokenType.IDENTIFIER);
        if (check(TokenType.LBRACKET)) {
            consume(TokenType.LBRACKET);
            consume(TokenType.RBRACKET);
        }
        consume(TokenType.SEMI);
    }

    private void assignmentStmt() {
        if (check(TokenType.IDENTIFIER) && peekNext().type == TokenType.LBRACKET
                && peekNextNext().type == TokenType.RBRACKET && peekNextNextNext().type == TokenType.ASSIGN) {
            consume(TokenType.IDENTIFIER);
            consume(TokenType.LBRACKET);
            consume(TokenType.RBRACKET);
            consume(TokenType.ASSIGN);
            expression();
            consume(TokenType.SEMI);
        } else {
            lValue();
            consume(TokenType.ASSIGN);
            expression();
            consume(TokenType.SEMI);
        }
    }

    private void lValue() {
        if (check(TokenType.WRITE)) {
            consume(TokenType.WRITE);
        } else if (check(TokenType.PUT)) {
            consume(TokenType.PUT);
        } else {
            consume(TokenType.IDENTIFIER);
            if (check(TokenType.LBRACKET)) {
                consume(TokenType.LBRACKET);
                expression();
                consume(TokenType.RBRACKET);
            }
        }
    }

    private void expression() {
        logicalExp();
    }

    private void logicalExp() {
        relationalExp();
        while (check(TokenType.EQ) || check(TokenType.NEQ)) {
            if (check(TokenType.EQ)) {
                consume(TokenType.EQ);
            } else {
                consume(TokenType.NEQ);
            }
            relationalExp();
        }
    }

    private void relationalExp() {
        additiveExp();
        while (check(TokenType.LT) || check(TokenType.LTE) || check(TokenType.GT) || check(TokenType.GTE)) {
            if (check(TokenType.LT)) {
                consume(TokenType.LT);
            } else if (check(TokenType.LTE)) {
                consume(TokenType.LTE);
            } else if (check(TokenType.GT)) {
                consume(TokenType.GT);
            } else {
                consume(TokenType.GTE);
            }
            additiveExp();
        }
    }

    private void additiveExp() {
        multiplicativeExp();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            if (check(TokenType.PLUS)) {
                consume(TokenType.PLUS);
            } else {
                consume(TokenType.MINUS);
            }
            multiplicativeExp();
        }
    }

    private void multiplicativeExp() {
        primary();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.MOD)) {
            if (check(TokenType.STAR)) {
                consume(TokenType.STAR);
            } else if (check(TokenType.SLASH)) {
                consume(TokenType.SLASH);
            } else {
                consume(TokenType.MOD);
            }
            primary();
        }
    }

    private void primary() {
        if (check(TokenType.INT_CONST)) {
            consume(TokenType.INT_CONST);
        } else if (check(TokenType.CHAR_CONST)) {
            consume(TokenType.CHAR_CONST);
        } else if (check(TokenType.STRING_CONST)) {
            consume(TokenType.STRING_CONST);
        } else if (check(TokenType.IDENTIFIER)) {
            consume(TokenType.IDENTIFIER);
            if (check(TokenType.LPAREN)) {
                consume(TokenType.LPAREN);
                if (!check(TokenType.RPAREN)) {
                    argumentList();
                }
                consume(TokenType.RPAREN);
            } else if (check(TokenType.LBRACKET)) {
                consume(TokenType.LBRACKET);
                expression();
                consume(TokenType.RBRACKET);
            }
        } else if (check(TokenType.WRITE)) {
            consume(TokenType.WRITE);
        } else if (check(TokenType.PUT)) {
            consume(TokenType.PUT);
        } else if (check(TokenType.READ)) {
            consume(TokenType.READ);
        } else if (check(TokenType.GET)) {
            consume(TokenType.GET);
        } else if (check(TokenType.LPAREN)) {
            consume(TokenType.LPAREN);
            expression();
            consume(TokenType.RPAREN);
        } else {
            error("Unexpected token: " + peek().lexeme);
            advance();
        }
    }

    private void argumentList() {
        expression();
        while (check(TokenType.COMMA)) {
            consume(TokenType.COMMA);
            expression();
        }
    }

    private boolean check(TokenType type) {
        if (isAtEnd())
            return false;
        return peek().type == type;
    }

    private Token consume(TokenType type) {
        if (check(type)) {
            return advance();
        }
        error("Expected token of type " + type + ", but got " + peek().lexeme);
        return advance();
    }

    private Token advance() {
        if (!isAtEnd())
            current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token peekNext() {
        if (current + 1 >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(current + 1);
    }

    private Token peekNextNext() {
        if (current + 2 >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(current + 2);
    }

    private Token peekNextNextNext() {
        if (current + 3 >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(current + 3);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private void error(String message) {
        hasError = true;
    }
}