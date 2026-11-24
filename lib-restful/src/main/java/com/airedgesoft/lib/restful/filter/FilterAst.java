package com.airedgesoft.lib.restful.filter;

import java.util.List;

/**
 * AIP-160 过滤表达式 AST（子集；v4-restful-api.md §7 filter 行，Tier B「过滤 AST 转换」）。
 *
 * <p>
 * <b>文法</b>（AIP-160）：{@code OR} / {@code AND}（<b>OR 优先级更高</b>，左结合；{@code a AND b OR c} = {@code a AND (b OR c)}）、 一元
 * {@code NOT} / {@code -}（否定，两者等价）、括号分组、比较符 {@code = != < <= > >= :}（{@code :} = has）、 操作数 = 字段路径（camelCase 点号路径，如
 * {@code owner.displayName} 寻址，仅左侧）/ 字符串字面量（双引号，{@code \"} 转义，{@code *} 通配）/ 数字字面量（整数与十进制）/ {@code true} /
 * {@code false} / {@code null}。 <b>右侧只接受字面量</b>（AIP-160:98-99，禁字段-字段比较）。函数调用与类型对齐校验为后续扩展。
 *
 * <p>
 * <b>消费方式</b>：持久层适配器实现 {@link FilterTranslator} 下推 SQL / 条件构造器： {@code ast.translate(translator)}——禁止各 BC
 * 自拼过滤字符串（白名单校验见 {@link FilterValidator}，解析见 {@link FilterParser}）。
 */
public sealed interface FilterAst permits FilterAst.Logical, FilterAst.Comparison, FilterAst.Not {

    /** 接受翻译器（visitor 下推）。 */
    <T> T translate(FilterTranslator<T> translator);

    /**
     * 逻辑节点：{@code kind} = AND / OR，children ≥ 2（扁平化后的同层操作数，左结合）。
     */
    record Logical(Kind kind, List<FilterAst> children) implements FilterAst {

        public Logical {
            children = List.copyOf(children);
        }

        public enum Kind {
            AND, OR
        }

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitLogical(kind, children.stream().map(child -> child.translate(translator)).toList());
        }
    }

    /** 一元否定节点（{@code NOT expr} / {@code -expr}，AIP-160:73-75）。 */
    record Not(FilterAst child) implements FilterAst {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitNot(child.translate(translator));
        }
    }

    /**
     * 比较节点：{@code field}（白名单域字段路径）op {@code value}（仅字面量；右侧禁字段引用，AIP-160:98-99）。
     */
    record Comparison(String field, Operator operator, Operand value) implements FilterAst {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitComparison(field, operator, value.translate(translator));
        }
    }

    /** 比较符。 */
    public enum Operator {
        EQ("="), NOT_EQUAL("!="), LESS("<"), LESS_OR_EQUAL("<="), GREATER(">"), GREATER_OR_EQUAL(">="), HAS(":");

        private final String token;

        Operator(String token) {
            this.token = token;
        }

        /** 词法 token 形态。 */
        public String token() {
            return token;
        }
    }

    /**
     * 操作数：字符串 / 数字（{@link java.math.BigDecimal}）/ 布尔 / null / 字段引用（仅左侧字段路径解析用；右侧禁字段，AIP-160:98-99）。
     */
    sealed interface Operand permits Text, Number, Boolean, Null, FieldRef {

        /** 经翻译器映射为适配器值形态（字符串沿用、数字转 BigDecimal、字段引用回字段名）。 */
        <T> T translate(FilterTranslator<T> translator);
    }

    /** 字符串字面量（已去引号与转义还原）。 */
    record Text(String value) implements Operand {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitText(value);
        }
    }

    /** 数字字面量。 */
    record Number(java.math.BigDecimal value) implements Operand {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitNumber(value);
        }
    }

    /** 布尔字面量。 */
    record Boolean(boolean value) implements Operand {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitBoolean(value);
        }
    }

    /** {@code null} 字面量（如 {@code deleteTime != null} 的回收站查询）。 */
    record Null() implements Operand {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitNull();
        }
    }

    /** 字段引用（字段间比较；字段名同样受 {@link FilterValidator} 白名单约束）。 */
    record FieldRef(String field) implements Operand {

        @Override
        public <T> T translate(FilterTranslator<T> translator) {
            return translator.visitFieldRef(field);
        }
    }
}
