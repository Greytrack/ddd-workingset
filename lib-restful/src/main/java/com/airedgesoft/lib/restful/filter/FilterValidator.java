package com.airedgesoft.lib.restful.filter;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.exception.FieldViolation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * filter 白名单校验（AIP-160；未知字段 → 400 {@link DomainRestCode#FILTER_FIELD_UNKNOWN} + 逐字段 {@code fieldViolations}）。
 *
 * <p>
 * 白名单域 = {@code @Filterable} 声明集（camelCase 点号路径，含 {@code owner.displayName} 等嵌套属性，规范 §11.1）；
 * 字段间比较的两侧字段同受约束。与解析器（{@link FilterParser}，语法 400）分工：语法非法与字段越界是两类错误码。
 */
public final class FilterValidator {

    private FilterValidator() {
    }

    /** 校验 AST 中出现的全部字段引用；未知字段一次性报全（规范 §8 一次返回全部违规）。 */
    public static void validate(FilterAst ast, Set<String> filterableFields) {
        if (ast == null) {
            return;
        }
        Set<String> referenced = new LinkedHashSet<>();
        collect(ast, referenced);
        List<String> unknown = referenced.stream().filter(field -> !filterableFields.contains(field)).toList();
        if (unknown.isEmpty()) {
            return;
        }
        DomainException exception = new DomainException(DomainRestCode.FILTER_FIELD_UNKNOWN,
                String.join(", ", unknown));
        throw exception.withFieldViolations(unknown.stream()
                .map(field -> FieldViolation.of(field, DomainRestCode.FILTER_FIELD_UNKNOWN, field)).toList());
    }

    private static void collect(FilterAst node, Set<String> sink) {
        if (node instanceof FilterAst.Logical logical) {
            for (FilterAst child : logical.children()) {
                collect(child, sink);
            }
        } else if (node instanceof FilterAst.Comparison comparison) {
            sink.add(comparison.field());
            if (comparison.value() instanceof FilterAst.FieldRef ref) {
                sink.add(ref.field());
            }
        } else if (node instanceof FilterAst.Not not) {
            // 否定节点（NOT / -）包裹的比较同样受白名单约束，否则 NOT (typo="x") 绕过校验
            collect(not.child(), sink);
        }
    }

    /** 解析 + 校验一站式（null / 空白入参 → null）。 */
    public static FilterAst parseAndValidate(String raw, Set<String> filterableFields) {
        FilterAst ast = FilterParser.parse(raw);
        validate(ast, filterableFields);
        return ast;
    }

    /** 收集 AST 引用的字段（诊断用；保序去重）。 */
    public static List<String> referencedFields(FilterAst ast) {
        Set<String> referenced = new LinkedHashSet<>();
        if (ast != null) {
            collect(ast, referenced);
        }
        return new ArrayList<>(referenced);
    }
}
