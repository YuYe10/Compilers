package cn.edu.fzu.ccds.compilerprinciples.mandrill.parser;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.Token;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.TokenType;

import java.util.List;

public class HandcraftParser implements Parser {
    private final List<Token> tokens;
    private int current;
    private boolean hasError;

    public HandcraftParser(List<Token> tokens) {
        this.tokens = tokens;
        this.current = 0;
        this.hasError = false;
    }

    @Override
    public boolean parse() {
        try {
            program();
            boolean success = !hasError && isAtEnd();
            System.err.println("Parser result: " + success + ", hasError: " + hasError + ", isAtEnd: " + isAtEnd());
            return success;
        } catch (ParseError e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

    private void program() {
        while (!isAtEnd()) {
            statement();
        }
    }

    private void statement() {
        if (match(TokenType.IF)) {
            ifStatement();
        } else if (match(TokenType.WHILE)) {
            whileStatement();
        } else if (match(TokenType.FUNC)) {
            functionDeclaration();
        } else if (match(TokenType.GLOBAL, TokenType.LOCAL)) {
            variableDeclaration();
        } else if (match(TokenType.RETURN)) {
            returnStatement();
        } else if (match(TokenType.BREAK, TokenType.CONTINUE)) {
            jumpStatement();
        } else if (match(TokenType.LBRACE)) {
            block();
        } else {
            expressionStatement();
        }
    }

    private void ifStatement() {
        // For if statements, require parentheses around condition
        consume(TokenType.LPAREN, "Expect '(' after 'if'");
        expression();
        consume(TokenType.RPAREN, "Expect ')' after condition");
        statement();
        if (match(TokenType.ELSE)) {
            statement();
        }
    }

    private void whileStatement() {
        consume(TokenType.LPAREN, "Expect '(' after 'while'");
        expression();
        consume(TokenType.RPAREN, "Expect ')' after condition");
        statement();
    }

    private void functionDeclaration() {
        consume(TokenType.IDENTIFIER, "Expect function name");
        consume(TokenType.LPAREN, "Expect '(' after function name");
        if (!check(TokenType.RPAREN)) {
            do {
                consume(TokenType.IDENTIFIER, "Expect parameter name");
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after parameters");
        block();
    }

    private void variableDeclaration() {
        // Handle both global/local identifier and global/local identifier = expression
        consume(TokenType.IDENTIFIER, "Expect variable name");
        if (match(TokenType.ASSIGN)) {
            expression();
        }
        consume(TokenType.SEMI, "Expect ';' after variable declaration");
    }

    private void returnStatement() {
        // Handle both return; and return expression;
        if (!check(TokenType.SEMI)) {
            expression();
        }
        consume(TokenType.SEMI, "Expect ';' after return");
    }

    private void jumpStatement() {
        consume(TokenType.SEMI, "Expect ';' after jump statement");
    }

    private void block() {
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statement();
        }
        if (!isAtEnd()) {
            consume(TokenType.RBRACE, "Expect '}' after block");
        }
    }

    private void expressionStatement() {
        expression();
        consume(TokenType.SEMI, "Expect ';' after expression");
    }

    private void expression() {
        assignment();
    }

    private void assignment() {
        // Try to parse a target that can be assigned to
        if (isAssignmentTarget()) {
            Token target = advance();
            
            // Check for array access or function call
            if (match(TokenType.LBRACKET)) {
                // Array assignment: a[index] = ... or a[] = ...
                if (match(TokenType.RBRACKET)) {
                    // Empty brackets: a[] = ...
                } else {
                    expression();
                    consume(TokenType.RBRACKET, "Expect ']' after index");
                }
            }
            
            if (match(TokenType.ASSIGN)) {
                expression();
            } else {
                // Not an assignment, backtrack and parse as equality
                current--;
                equality();
            }
        } else {
            equality();
        }
    }

    private boolean isAssignmentTarget() {
        return check(TokenType.IDENTIFIER) || check(TokenType.WRITE) || 
               check(TokenType.PUT) || check(TokenType.READ) || check(TokenType.GET);
    }

    private void equality() {
        comparison();
        while (match(TokenType.EQ, TokenType.NEQ)) {
            comparison();
        }
    }

    private void comparison() {
        term();
        while (match(TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE)) {
            term();
        }
    }

    private void term() {
        factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            factor();
        }
    }

    private void factor() {
        unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            unary();
        }
    }

    private void unary() {
        if (match(TokenType.MINUS)) {
            unary();
        } else {
            primary();
        }
    }

    private void primary() {
        if (match(TokenType.INT_CONST, TokenType.CHAR_CONST, TokenType.STRING_CONST)) {
            // Literals
        } else if (match(TokenType.IDENTIFIER, TokenType.WRITE, TokenType.PUT, TokenType.READ, TokenType.GET)) {
            // Handle function calls and array accesses
            while (true) {
                if (match(TokenType.LPAREN)) {
                    // Function call
                    if (!check(TokenType.RPAREN)) {
                        do {
                            expression();
                        } while (match(TokenType.COMMA));
                    }
                    consume(TokenType.RPAREN, "Expect ')' after arguments");
                } else if (match(TokenType.LBRACKET)) {
                    // Array access
                    if (match(TokenType.RBRACKET)) {
                        // Empty brackets: arr[]
                    } else {
                        expression();
                        consume(TokenType.RBRACKET, "Expect ']' after index");
                    }
                } else {
                    break;
                }
            }
        } else if (match(TokenType.LPAREN)) {
            expression();
            consume(TokenType.RPAREN, "Expect ')' after expression");
        } else {
            throw error(peek(), "Expect expression");
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private ParseError error(Token token, String message) {
        hasError = true;
        return new ParseError(token, message);
    }

    private static class ParseError extends RuntimeException {
        final Token token;
        final String message;

        ParseError(Token token, String message) {
            this.token = token;
            this.message = message;
        }
    }
}