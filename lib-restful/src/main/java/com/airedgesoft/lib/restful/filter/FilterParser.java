package com.airedgesoft.lib.restful.filter;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AIP-160 子集递归下降解析器（手写，无外部依赖；文法见 {@link FilterAst}）。
 *
 * <p>
 * <b>错误语义</b>：任何词法 / 语法错误（非法字符、缺操作数、括号不配对、悬挂 AND/OR 等）→ 400 {@link DomainRestCode#FILTER_SYNTAX_INVALID}（detail
 * 带出错位置），<b>禁止静默忽略</b>（静默等于把过滤条件整段丢弃——返回全集即越权读）。
 *
 * <p>
 * <b>优先级</b>（AIP-160:65-67）：{@code OR} <b>最高</b>（结合更紧）；{@code AND} 次之（左结合）；括号可覆盖。{@code a AND b OR c} =
 * {@code a AND (b OR c)}。 一元 {@code NOT} / {@code -} 否定；比较右侧只接受字面量（AIP-160:98-99）。字段路径 / 字面量空白分隔，与 AIP-160 词法一致。
 */
public final class FilterParser {

    private FilterParser() {
    }

    /** 解析 filter 表达式（null / 空白 → null，表示不过滤）。 */
    public static FilterAst parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        TokenStream tokens = new TokenStream(raw);
        FilterAst ast = andExpression(tokens);
        if (!tokens.atEnd()) {
            throw syntaxError(raw, tokens.position(), "存在未消费的输入");
        }
        return ast;
    }

    // ── 文法（OR 优先级高于 AND，AIP-160:65-67）：andExpr := orExpr (AND orExpr)* ; orExpr := unary (OR unary)* ;
    // unary := (NOT | '-') unary | primary ; primary := '(' andExpr ')' | comparison ──

    private static FilterAst andExpression(TokenStream tokens) {
        List<FilterAst> children = new ArrayList<>();
        children.add(orExpression(tokens));
        while (tokens.matchKeyword("AND")) {
            children.add(orExpression(tokens));
        }
        return children.size() == 1 ? children.get(0) : new FilterAst.Logical(FilterAst.Logical.Kind.AND, children);
    }

    private static FilterAst orExpression(TokenStream tokens) {
        List<FilterAst> children = new ArrayList<>();
        children.add(unary(tokens));
        while (tokens.matchKeyword("OR")) {
            children.add(unary(tokens));
        }
        return children.size() == 1 ? children.get(0) : new FilterAst.Logical(FilterAst.Logical.Kind.OR, children);
    }

    private static FilterAst unary(TokenStream tokens) {
        if (tokens.matchKeyword("NOT") || tokens.matchSymbol("-")) {
            return new FilterAst.Not(unary(tokens));
        }
        return primary(tokens);
    }

    private static FilterAst primary(TokenStream tokens) {
        if (tokens.matchSymbol("(")) {
            FilterAst inner = andExpression(tokens);
            if (!tokens.matchSymbol(")")) {
                throw syntaxError(tokens.source(), tokens.position(), "缺少右括号");
            }
            return inner;
        }
        return comparison(tokens);
    }

    private static FilterAst.Comparison comparison(TokenStream tokens) {
        FilterAst.Operand left = operand(tokens);
        FilterAst.Operator operator = operator(tokens);
        FilterAst.Operand right = operand(tokens);
        if (!(left instanceof FilterAst.FieldRef field)) {
            throw syntaxError(tokens.source(), tokens.position(), "比较左侧必须是字段路径");
        }
        if (right instanceof FilterAst.FieldRef) {
            throw syntaxError(tokens.source(), tokens.position(), "比较右侧只接受字面量（AIP-160:98-99，禁字段-字段比较）");
        }
        return new FilterAst.Comparison(field.field(), operator, right);
    }

    private static FilterAst.Operator operator(TokenStream tokens) {
        // 双字符操作符优先匹配（<= / >= / != 不得被 < / > 截断）
        List<FilterAst.Operator> ordered = java.util.Arrays.stream(FilterAst.Operator.values())
                .sorted(java.util.Comparator.comparingInt((FilterAst.Operator op) -> op.token().length()).reversed())
                .toList();
        for (FilterAst.Operator operator : ordered) {
            if (tokens.matchOperator(operator.token())) {
                return operator;
            }
        }
        throw syntaxError(tokens.source(), tokens.position(), "缺少比较符（= != < <= > >= :）");
    }

    private static FilterAst.Operand operand(TokenStream tokens) {
        String literal = tokens.matchStringLiteral();
        if (literal != null) {
            return new FilterAst.Text(literal);
        }
        String number = tokens.matchNumber();
        if (number != null) {
            return new FilterAst.Number(new BigDecimal(number));
        }
        if (tokens.matchKeyword("TRUE")) {
            return new FilterAst.Boolean(true);
        }
        if (tokens.matchKeyword("FALSE")) {
            return new FilterAst.Boolean(false);
        }
        if (tokens.matchKeyword("NULL")) {
            return new FilterAst.Null();
        }
        String identifier = tokens.matchIdentifier();
        if (identifier != null) {
            return new FilterAst.FieldRef(identifier);
        }
        throw syntaxError(tokens.source(), tokens.position(), "缺少操作数（字段路径 / 字面量）");
    }

    private static DomainException syntaxError(String source, int position, String reason) {
        throw new DomainException(DomainRestCode.FILTER_SYNTAX_INVALID, reason + " @" + position + ": " + source);
    }

    /** 词法流：逐 token 匹配（全部消费由文法驱动；match* 前进或回退）。 */
    private static final class TokenStream {

        private final String source;
        private int position;

        TokenStream(String source) {
            this.source = source;
        }

        String source() {
            return source;
        }

        int position() {
            return position;
        }

        boolean atEnd() {
            skipWhitespace();
            return position >= source.length();
        }

        boolean matchKeyword(String keyword) {
            skipWhitespace();
            int mark = position;
            if (source.regionMatches(true, position, keyword, 0, keyword.length())) {
                int after = position + keyword.length();
                if (after >= source.length() || isBoundary(source.charAt(after))) {
                    position = after;
                    return true;
                }
            }
            position = mark;
            return false;
        }

        boolean matchSymbol(String symbol) {
            skipWhitespace();
            if (source.startsWith(symbol, position)) {
                position += symbol.length();
                return true;
            }
            return false;
        }

        boolean matchOperator(String operator) {
            return matchSymbol(operator);
        }

        String matchStringLiteral() {
            skipWhitespace();
            if (position >= source.length() || source.charAt(position) != '"') {
                return null;
            }
            StringBuilder value = new StringBuilder();
            position++;
            while (position < source.length()) {
                char current = source.charAt(position);
                if (current == '\\' && position + 1 < source.length()) {
                    value.append(source.charAt(++position));
                    position++;
                    continue;
                }
                if (current == '"') {
                    position++;
                    return value.toString();
                }
                value.append(current);
                position++;
            }
            throw new DomainException(DomainRestCode.FILTER_SYNTAX_INVALID, "字符串字面量未闭合 @" + position);
        }

        String matchNumber() {
            skipWhitespace();
            int start = position;
            if (position < source.length() && (source.charAt(position) == '-' || source.charAt(position) == '+')) {
                position++;
            }
            boolean digits = false;
            boolean decimal = false;
            while (position < source.length()) {
                char current = source.charAt(position);
                if (Character.isDigit(current)) {
                    digits = true;
                    position++;
                } else if (current == '.' && !decimal && position + 1 < source.length()
                        && Character.isDigit(source.charAt(position + 1))) {
                    decimal = true;
                    position++;
                } else {
                    break;
                }
            }
            if (!digits) {
                position = start;
                return null;
            }
            return source.substring(start, position);
        }

        String matchIdentifier() {
            skipWhitespace();
            int start = position;
            if (position >= source.length() || !isIdentifierStart(source.charAt(position))) {
                return null;
            }
            position++;
            while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position))
                    || source.charAt(position) == '_' || source.charAt(position) == '.')) {
                position++;
            }
            return source.substring(start, position);
        }

        private static boolean isBoundary(char candidate) {
            return Character.isWhitespace(candidate) || "()<>=!,".indexOf(candidate) >= 0;
        }

        private static boolean isIdentifierStart(char candidate) {
            return Character.isLetter(candidate) || candidate == '_';
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }
    }
}
