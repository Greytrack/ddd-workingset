package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 引用方上下文装配实现（infrastructure 读侧）：按引用方 id 装配 v3 冗余列事实
 * （{@code common_id/instance_type/work_space/workingset_id/tracker}），填充 {@code air_inst_tab_reference} 写侧行。
 *
 * <p>
 * 冗余列不进领域（裁决与类图口径）：本端口是仓储写路径的宿主上下文事实源，经 {@code air_inst_common} 直查。
 */
@Component
public class ReferenceSourceContextProviderImpl implements ReferenceRepositoryImpl.ReferenceSourceContextProvider {

    private static final String SELECT_CONTEXT = """
            SELECT common_id, "class" AS instance_type, work_space, workingset_id, tracker, revision
            FROM air_inst_common WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ReferenceSourceContextProviderImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReferenceRepositoryImpl.SourceContext load(Long sourceId) {
        return jdbcTemplate.queryForObject(SELECT_CONTEXT,
                (rs, rowNum) -> new ReferenceRepositoryImpl.SourceContext(rs.getLong("common_id"),
                        rs.getLong("instance_type"), rs.getLong("work_space"), rs.getLong("workingset_id"),
                        rs.getLong("tracker"), rs.getInt("revision")),
                sourceId);
    }
}
