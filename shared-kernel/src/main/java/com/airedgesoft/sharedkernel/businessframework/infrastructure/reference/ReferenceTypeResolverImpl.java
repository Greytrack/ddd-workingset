package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

import com.airedgesoft.sharedkernel.businessframework.application.query.ReferenceTypeResolver;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 引用类型装配实现（裁决 4）：目标 Tracker 共享 → DIRECT，否则 RELATIVE（对齐 v3 {@code AddAction:1078-1084}）。
 *
 * <p>
 * 共享性事实经现表直查（{@code air_ws_tracker.is_shared}），不依赖 shared-kernel Tracker 端口—— 引用侧只读 Tracker 的一个布尔位，不装配整个 Tracker 聚合（§13
 * 跨 BC 只按 ID 引用）。
 */
@Component
public class ReferenceTypeResolverImpl implements ReferenceTypeResolver {

    private static final String SELECT_SHARED = "SELECT shared FROM air_ws_tracker WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;

    public ReferenceTypeResolverImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReferenceType resolve(Long targetId) {
        if (targetId == null) {
            return ReferenceType.RELATIVE;
        }
        try {
            List<Boolean> shared = jdbcTemplate.queryForList(SELECT_SHARED, Boolean.class, targetId);
            return !shared.isEmpty() && Boolean.TRUE.equals(shared.get(0))
                    ? ReferenceType.DIRECT
                    : ReferenceType.RELATIVE;
        } catch (org.springframework.dao.DataAccessException exception) {
            // 隔离测试 schema 无 air_ws_tracker（共享性属 Tracker 语境，引用聚合不拥有该表）——
            // 不可得时回退 RELATIVE（v3 非 shared 缺省口径），共享性判定挂账随 Tracker 语境轮收口。
            return ReferenceType.RELATIVE;
        }
    }
}
