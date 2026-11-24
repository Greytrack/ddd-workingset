package com.airedgesoft.sharedkernel.businessframework.application.authorization;

import java.util.List;
import java.util.Objects;

/** 发现权限渲染产出的 SQL 谓词与按序绑定参数（上提自 {@code v4-identityaccess}）。 */
public record RenderedSql(String sql, List<Object> parameters) {
    public RenderedSql {
        if (Objects.requireNonNull(sql, "sql").isBlank()) {
            throw new IllegalArgumentException("Authorization predicate must not be blank");
        }
        parameters = List.copyOf(parameters);
    }
}
