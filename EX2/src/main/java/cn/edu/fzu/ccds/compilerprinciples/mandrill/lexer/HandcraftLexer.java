package cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer;

import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.Token;
import cn.edu.fzu.ccds.compilerprinciples.mandrill.lexer.tokens.TokenType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandcraftLexer {
    
    private PushbackReader reader;
    private List<Token> tokens;
    private int line;
    private int column;
    private int currentChar;
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("if", TokenType.IF);
        keywords.put("else", TokenType.ELSE);
        keywords.put("while", TokenType.WHILE);
        keywords.put("read", TokenType.READ);
        keywords.put("put", TokenType.PUT);
        keywords.put("write", TokenType.WRITE);
        keywords.put("get", TokenType.GET);
        keywords.put("func", TokenType.FUNC);
        keywords.put("global", TokenType.GLOBAL);
        keywords.put("local", TokenType.LOCAL);
        keywords.put("return", TokenType.RETURN);
        keywords.put("break", TokenType.BREAK);
        keywords.put("continue", TokenType.CONTINUE);
    }

    public List<Token> scanTokens() {
        tokens = new ArrayList<>();
        line = 1;
        column = 0;
        
        try {
            while (true) {
                currentChar = reader.read();
                if (currentChar == -1) {
                    break;
                }
                column++;
                scanToken();
            }
            tokens.add(new Token(TokenType.EOF, "<EOF>", line, column + 1));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return tokens;
    }

    private void scanToken() throws IOException {
        char c = (char) currentChar;
        int startColumn = column;
        
        switch (c) {
            case ' ':
            case '\t':
            case '\r':
                break;
            case '\n':
                line++;
                column = 0;
                break;
            case '(':
                addToken(TokenType.LPAREN, "(", startColumn);
                break;
            case ')':
                addToken(TokenType.RPAREN, ")", startColumn);
                break;
            case '[':
                addToken(TokenType.LBRACKET, "[", startColumn);
                break;
            case ']':
                addToken(TokenType.RBRACKET, "]", startColumn);
                break;
            case '{':
                addToken(TokenType.LBRACE, "{", startColumn);
                break;
            case '}':
                addToken(TokenType.RBRACE, "}", startColumn);
                break;
            case ',':
                addToken(TokenType.COMMA, ",", startColumn);
                break;
            case ';':
                addToken(TokenType.SEMI, ";", startColumn);
                break;
            case '+':
                addToken(TokenType.PLUS, "+", startColumn);
                break;
            case '-':
                addToken(TokenType.MINUS, "-", startColumn);
                break;
            case '*':
                addToken(TokenType.STAR, "*", startColumn);
                break;
            case '/':
                addToken(TokenType.SLASH, "/", startColumn);
                break;
            case '%':
                addToken(TokenType.MOD, "%", startColumn);
                break;
            case '=':
                int nextChar = reader.read();
                if (nextChar == '=') {
                    column++;
                    addToken(TokenType.EQ, "==", startColumn);
                } else {
                    addToken(TokenType.ASSIGN, "=", startColumn);
                    if (nextChar != -1) {
                        reader.unread(nextChar);
                    }
                }
                break;
            case '!':
                nextChar = reader.read();
                if (nextChar == '=') {
                    column++;
                    addToken(TokenType.NEQ, "!=", startColumn);
                } else {
                    if (nextChar != -1) {
                        reader.unread(nextChar);
                    }
                }
                break;
            case '<':
                nextChar = reader.read();
                if (nextChar == '=') {
                    column++;
                    addToken(TokenType.LTE, "<=", startColumn);
                } else {
                    addToken(TokenType.LT, "<", startColumn);
                    if (nextChar != -1) {
                        reader.unread(nextChar);
                    }
                }
                break;
            case '>':
                nextChar = reader.read();
                if (nextChar == '=') {
                    column++;
                    addToken(TokenType.GTE, ">=", startColumn);
                } else {
                    addToken(TokenType.GT, ">", startColumn);
                    if (nextChar != -1) {
                        reader.unread(nextChar);
                    }
                }
                break;
            case '\'':
                scanCharConst();
                break;
            case '"':
                scanStringConst();
                break;
            default:
                if (Character.isDigit(c)) {
                    scanNumber();
                } else if (Character.isLetter(c) || c == '_') {
                    scanIdentifier();
                }
                break;
        }
    }

    private void scanCharConst() throws IOException {
        int startColumn = column;
        StringBuilder lexeme = new StringBuilder();
        
        int ch = reader.read();
        column++;
        if (ch == -1) {
            return;
        }
        
        if (ch == '\\') {
            lexeme.append((char) ch);
            ch = reader.read();
            column++;
            if (ch == -1) {
                return;
            }
            lexeme.append((char) ch);
        } else if (ch != '\'') {
            lexeme.append((char) ch);
        }
        
        ch = reader.read();
        column++;
        if (ch == '\'') {
            tokens.add(new Token(TokenType.CHAR_CONST, lexeme.toString(), line, startColumn));
        } else if (ch != -1) {
            reader.unread(ch);
            column--;
        }
    }

    private void scanStringConst() throws IOException {
        int startColumn = column;
        StringBuilder lexeme = new StringBuilder();
        
        int ch = reader.read();
        column++;
        while (ch != -1 && ch != '"') {
            lexeme.append((char) ch);
            ch = reader.read();
            column++;
        }
        
        if (ch == '"') {
            tokens.add(new Token(TokenType.STRING_CONST, lexeme.toString(), line, startColumn));
        } else if (ch != -1) {
            reader.unread(ch);
            column--;
        }
    }

    private void scanNumber() throws IOException {
        int startColumn = column;
        StringBuilder lexeme = new StringBuilder();
        
        while (currentChar != -1 && Character.isDigit(currentChar)) {
            lexeme.append((char) currentChar);
            column++;
            currentChar = reader.read();
        }
        
        if (currentChar != -1) {
            reader.unread(currentChar);
            column--;
        }
        
        tokens.add(new Token(TokenType.INT_CONST, lexeme.toString(), line, startColumn));
    }

    private void scanIdentifier() throws IOException {
        int startColumn = column;
        StringBuilder lexeme = new StringBuilder();
        
        while (currentChar != -1 && (Character.isLetterOrDigit(currentChar) || currentChar == '_')) {
            lexeme.append((char) currentChar);
            column++;
            currentChar = reader.read();
        }
        
        if (currentChar != -1) {
            reader.unread(currentChar);
            column--;
        }
        
        String text = lexeme.toString();
        TokenType type = keywords.get(text);
        if (type == null) {
            type = TokenType.IDENTIFIER;
        }
        
        tokens.add(new Token(type, text, line, startColumn));
    }

    private void addToken(TokenType type, String lexeme, int startColumn) {
        tokens.add(new Token(type, lexeme, line, startColumn));
    }

    
    // 这两个函数不要进行改动
    public HandcraftLexer(InputStream is) {
        this.reader = new PushbackReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws IOException {
        InputStream inputStream = args.length > 0 && !args[0].equals("-") ? new FileInputStream(args[0]) : System.in;
        PrintStream printStream = args.length > 1 && !args[1].equals("-") ? new PrintStream(args[1]) : System.out;
        try (inputStream; printStream) {
            HandcraftLexer lexer = new HandcraftLexer(inputStream);
            List<Token> tokens = lexer.scanTokens();

            for (Token token : tokens) {
                printStream.println(token);
            }
        }
    }
}