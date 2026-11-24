package com.airedgesoft.lib.restful.filter;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AIP-160 子集解析器 / 白名单校验单测（组件 12）。
 */
class FilterParserTest {

    @Test
    void parse_nullOrBlank_returnsNull() {
        assertThat(FilterParser.parse(null)).isNull();
        assertThat(FilterParser.parse("   ")).isNull();
    }

    @Test
    void parse_singleComparison_withLiterals() {
        FilterAst ast = FilterParser.parse("status = \"ACTIVE\"");
        assertThat(ast).isInstanceOf(FilterAst.Comparison.class);
        FilterAst.Comparison comparison = (FilterAst.Comparison) ast;
        assertThat(comparison.field()).isEqualTo("status");
        assertThat(comparison.operator()).isEqualTo(FilterAst.Operator.EQ);
        assertThat(comparison.value()).isEqualTo(new FilterAst.Text("ACTIVE"));
    }

    @Test
    void parse_operatorsAndEscapes() {
        assertThat(((FilterAst.Comparison) FilterParser.parse("a != 1")).operator())
                .isEqualTo(FilterAst.Operator.NOT_EQUAL);
        assertThat(((FilterAst.Comparison) FilterParser.parse("a <= 1.5")).value())
                .isEqualTo(new FilterAst.Number(new java.math.BigDecimal("1.5")));
        assertThat(((FilterAst.Comparison) FilterParser.parse("a >= -2")).value())
                .isEqualTo(new FilterAst.Number(new java.math.BigDecimal("-2")));
        assertThat(((FilterAst.Comparison) FilterParser.parse("note = \"say \\\"hi\\\"\"")).value())
                .isEqualTo(new FilterAst.Text("say \"hi\""));
    }

    @Test
    void parse_boolAndNullLiterals() {
        FilterAst ast = FilterParser.parse("deleteTime != null AND archived = false");
        assertThat(((FilterAst.Comparison) ((FilterAst.Logical) ast).children().get(0)).value())
                .isInstanceOf(FilterAst.Null.class);
        assertThat(((FilterAst.Comparison) ((FilterAst.Logical) ast).children().get(1)).value())
                .isEqualTo(new FilterAst.Boolean(false));
    }

    @Test
    void parse_orBindsTighterThanAnd() {
        // AIP-160:65-67：OR 优先级高于 AND → a=1 OR b=2 AND c=3 = (a=1 OR b=2) AND c=3
        FilterAst ast = FilterParser.parse("a = 1 OR b = 2 AND c = 3");
        assertThat(ast).isInstanceOf(FilterAst.Logical.class);
        FilterAst.Logical and = (FilterAst.Logical) ast;
        assertThat(and.kind()).isEqualTo(FilterAst.Logical.Kind.AND);
        assertThat(and.children().get(0)).isInstanceOf(FilterAst.Logical.class);
        assertThat(((FilterAst.Logical) and.children().get(0)).kind()).isEqualTo(FilterAst.Logical.Kind.OR);
        assertThat(and.children().get(1)).isInstanceOf(FilterAst.Comparison.class);
    }

    @Test
    void parse_aipPrecedenceExample() {
        // AIP-160:65-67 原文示例：a AND b OR c = a AND (b OR c)
        FilterAst ast = FilterParser.parse("a = 1 AND b = 2 OR c = 3");
        FilterAst.Logical and = (FilterAst.Logical) ast;
        assertThat(and.kind()).isEqualTo(FilterAst.Logical.Kind.AND);
        assertThat(and.children().get(0)).isInstanceOf(FilterAst.Comparison.class);
        assertThat(((FilterAst.Logical) and.children().get(1)).kind()).isEqualTo(FilterAst.Logical.Kind.OR);
    }

    @Test
    void parse_parenthesesOverridePrecedence() {
        FilterAst ast = FilterParser.parse("(a = 1 OR b = 2) AND c = 3");
        FilterAst.Logical and = (FilterAst.Logical) ast;
        assertThat(and.kind()).isEqualTo(FilterAst.Logical.Kind.AND);
        assertThat(and.children().get(0)).isInstanceOf(FilterAst.Logical.class); // 括号分组节点
    }

    @Test
    void parse_fieldToFieldComparison_rejected() {
        // AIP-160:98-99：右侧只接受字面量，禁字段-字段比较
        assertThatThrownBy(() -> FilterParser.parse("start < end")).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void parse_hasOperator() {
        FilterAst.Comparison comparison = (FilterAst.Comparison) FilterParser.parse("title : \"foo\"");
        assertThat(comparison.operator()).isEqualTo(FilterAst.Operator.HAS);
    }

    @Test
    void parse_notAndMinusNegation() {
        assertThat(FilterParser.parse("NOT status = \"A\"")).isInstanceOf(FilterAst.Not.class);
        assertThat(FilterParser.parse("- status = \"A\"")).isInstanceOf(FilterAst.Not.class);
    }

    @Test
    void parse_syntaxErrors_failFast400() {
        for (String broken : new String[]{"a =", "= 1", "a = ", "a > 'b'", "a AND b", "(a = 1", "a = 1 AND (b = 2",
                "a = 1 OR", "status = \"未闭合", "a === 1", "1 = a", "a = 1 b = 2"}) {
            assertThatThrownBy(() -> FilterParser.parse(broken)).as("语法错误用例: %s", broken)
                    .isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).getType())
                    .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
        }
    }

    @Test
    void validate_unknownFields_failFastWithViolations() {
        FilterAst ast = FilterParser.parse("status = \"A\" AND typo > 1");
        assertThatThrownBy(() -> FilterValidator.validate(ast, Set.of("status"))).isInstanceOf(DomainException.class)
                .satisfies(e -> assertThat(((DomainException) e).getFieldViolations()).singleElement()
                        .satisfies(v -> assertThat(v.code()).isEqualTo(DomainRestCode.FILTER_FIELD_UNKNOWN)));
    }

    @Test
    void validate_unknownFieldUnderNegation_stillRejected() {
        // NOT / - 包裹的比较不得绕过白名单（FilterValidator.collect 须递归 Not 子节点）
        for (String expr : new String[]{"NOT (typo = \"x\")", "- typo = \"x\"", "NOT status = \"A\" AND typo > 1"}) {
            FilterAst ast = FilterParser.parse(expr);
            assertThatThrownBy(() -> FilterValidator.validate(ast, Set.of("status"))).as("否定表达式: %s", expr)
                    .isInstanceOf(DomainException.class)
                    .satisfies(e -> assertThat(((DomainException) e).getFieldViolations())
                            .anySatisfy(v -> assertThat(v.field()).isEqualTo("typo")));
        }
    }

    @Test
    void parseAndValidate_passesForWhitelistedFields() {
        FilterAst ast = FilterValidator.parseAndValidate("customFields.riskLevel = \"HIGH\" OR status != \"DONE\"",
                Set.of("customFields.riskLevel", "status"));
        assertThat(FilterValidator.referencedFields(ast)).containsExactly("customFields.riskLevel", "status");
    }

    @Test
    void translate_visitorReceivesFlattenedTree() {
        StringBuilder shape = new StringBuilder();
        FilterAst ast = FilterParser.parse("a = 1 OR (b = \"x\" AND c = true)");
        String result = ast.translate(new FilterTranslator<>() {
            @Override
            public String visitLogical(FilterAst.Logical.Kind kind, List<String> children) {
                shape.append(kind).append(children);
                return kind + "(" + String.join(",", children) + ")";
            }

            @Override
            public String visitNot(String child) {
                return "NOT(" + child + ")";
            }

            @Override
            public String visitComparison(String field, FilterAst.Operator operator, String value) {
                shape.append("[").append(field).append(operator).append(value).append("]");
                return field + operator + value;
            }

            @Override
            public String visitText(String value) {
                return "'" + value + "'";
            }

            @Override
            public String visitNumber(java.math.BigDecimal value) {
                return value.toPlainString();
            }

            @Override
            public String visitBoolean(boolean value) {
                return String.valueOf(value);
            }

            @Override
            public String visitNull() {
                return "NULL";
            }

            @Override
            public String visitFieldRef(String field) {
                return field;
            }
        });
        assertThat(result).isEqualTo("OR(aEQ1,AND(bEQ'x',cEQtrue))"); // visitor 收到枚举形态操作符（token() 供 SQL 用）
        assertThat(shape).contains("AND");
    }
}
