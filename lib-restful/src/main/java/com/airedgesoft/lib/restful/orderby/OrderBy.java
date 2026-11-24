package com.airedgesoft.lib.restful.orderby;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code orderBy} 多字段解析与白名单校验（v4-restful-api.md §7 List 行 / AIP-132；Tier B「排序白名单保序」）。
 *
 * <p>
 * <b>语法</b>：{@code ?orderBy=field1 desc,field2 asc}（逗号分段；段内空白分隔字段与方向；方向大小写不敏感、缺省 {@code asc}； 仅允许
 * {@code asc}/{@code desc}，其他 → 400 {@link DomainRestCode#ORDER_BY_FIELD_UNKNOWN}）。
 *
 * <p>
 * <b>白名单</b>：字段不在 {@code @Sortable} 声明集内 → 400（含逐字段 {@code fieldViolations}，一次返回全部）。
 *
 * <p>
 * <b>主键兜底</b>：{@link #resolve(String, Set, String)} 在解析结果<b>末尾强制追加主键升序</b>（用户未显式声明主键时）——排序全序稳定是游标分页
 * （{@code PageTokens}）正确性的前提；主键已被用户声明时尊重用户方向，不重复追加。
 *
 * <p>
 * <b>SQL 产出</b>：{@link #render()} 输出 {@code field1 DESC, field2 ASC, id ASC}——字段集来自开发者白名单（非用户输入），可直接拼入 ORDER BY；如需「契约字段
 * → 列名」映射，先以映射后的列名构造白名单。
 */
public record OrderBy(List<SortKey> keys) {

    /** 解析 + 白名单校验（无主键兜底；排序稳定性由调用方保证）。 */
    public static OrderBy resolve(String raw, Set<String> allowedFields) {
        if (raw == null || raw.isBlank()) {
            return new OrderBy(List.of());
        }
        List<SortKey> keys = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String segment : raw.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            String field = parts[0].trim();
            SortDirection direction = SortDirection.ASC;
            if (parts.length == 2) {
                String dir = parts[1].trim().toLowerCase(Locale.ROOT);
                if ("desc".equals(dir)) {
                    direction = SortDirection.DESC;
                } else if (!"asc".equals(dir)) {
                    throw new DomainException(DomainRestCode.ORDER_BY_FIELD_UNKNOWN, trimmed);
                }
            } else if (parts.length > 2) {
                throw new DomainException(DomainRestCode.ORDER_BY_FIELD_UNKNOWN, trimmed);
            }
            if (!allowedFields.contains(field)) {
                unknown.add(field);
                continue;
            }
            keys.add(new SortKey(field, direction));
        }
        if (!unknown.isEmpty()) {
            DomainException ex = new DomainException(DomainRestCode.ORDER_BY_FIELD_UNKNOWN, String.join(", ", unknown));
            throw ex.withFieldViolations(unknown.stream().map(field -> com.airedgesoft.lib.exception.FieldViolation
                    .of(field, DomainRestCode.ORDER_BY_FIELD_UNKNOWN, field)).toList());
        }
        return new OrderBy(List.copyOf(keys));
    }

    /** 解析 + 白名单校验 + 末尾主键升序兜底（游标分页场景使用本入口）。 */
    public static OrderBy resolve(String raw, Set<String> allowedFields, String primaryKey) {
        OrderBy resolved = resolve(raw, allowedFields);
        if (primaryKey != null && !primaryKey.isBlank() && !resolved.includes(primaryKey)) {
            List<SortKey> keys = new ArrayList<>(resolved.keys());
            keys.add(new SortKey(primaryKey, SortDirection.ASC));
            return new OrderBy(List.copyOf(keys));
        }
        return resolved;
    }

    /** 是否声明了某字段。 */
    public boolean includes(String field) {
        return keys.stream().anyMatch(key -> key.field().equals(field));
    }

    /** SQL ORDER BY 片段（字段已白名单校验；空掩码 → 空串，调用方不拼 ORDER BY 子句）。 */
    public String render() {
        StringBuilder sql = new StringBuilder();
        for (SortKey key : keys) {
            if (!sql.isEmpty()) {
                sql.append(", ");
            }
            sql.append(key.field()).append(' ').append(key.direction().sql());
        }
        return sql.toString();
    }

    /** 单个排序键：契约字段名（白名单域）+ 方向。 */
    public record SortKey(String field, SortDirection direction) {
    }
}
