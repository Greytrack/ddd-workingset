package com.airedgesoft.sharedkernel.businessframework.infrastructure.association;

import com.airedgesoft.lib.jdbc.StoredRow;
import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationRepository;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;
import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationType;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.association.AssociationContextProvider.AssociationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.airedgesoft.sharedkernel.businessframework.infrastructure.association.AssociationStorage.TABLE;

/**
 * 关联行聚合仓储实现（Spring JDBC 直写现表 {@code air_inst_tab_association}；继承传播怀疑父仓储——触发口径 8）。
 *
 * <p>
 * <b>写侧</b>：INSERT 单版次产物（系统列 + 上下文冗余列 + 业务列）、UPDATE 业务列 + 审计列（不推 lock_version，对齐 v3
 * {@code UpdateBuilder.defaultFiledUpdate} 只写 updated_by/updated_time）、DELETE 物理删除；规则三表与关联行同事务。
 *
 * <p>
 * <b>读侧</b>：按 id / 上游 / 下游取行，NULL 宽容；规则按 {@code relationship_id} 批量装配。
 */
@Repository
public class AssociationRepositoryImpl implements AssociationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AssociationContextProvider contextProvider;

    public AssociationRepositoryImpl(JdbcTemplate jdbcTemplate, AssociationContextProvider contextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.contextProvider = contextProvider;
    }

    @Override
    public List<Association> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<StoredRow> rows = query(
                "SELECT * FROM " + TABLE + " WHERE " + AssociationStorage.ID + " IN (" + placeholders(ids.size()) + ")",
                ids.toArray());
        return attachRules(rows);
    }

    @Override
    public Optional<Association> findById(Long associationId) {
        if (associationId == null) {
            return Optional.empty();
        }
        List<StoredRow> rows = query("SELECT * FROM " + TABLE + " WHERE " + AssociationStorage.ID + " = ?",
                associationId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(attachRules(rows).get(0));
    }

    @Override
    public List<Association> findUpstreamRows(Long upstreamObjectId) {
        // 该对象是目标端（related_instance_id 侧）
        return attachRules(query("SELECT * FROM " + TABLE + " WHERE " + AssociationStorage.RELATED_INSTANCE_ID + " = ?",
                upstreamObjectId));
    }

    @Override
    public List<Association> findDownstreamRows(Long downstreamObjectId) {
        // 该对象是源端（instance_id 侧）
        return attachRules(query("SELECT * FROM " + TABLE + " WHERE " + AssociationStorage.INSTANCE_ID + " = ?",
                downstreamObjectId));
    }

    @Override
    public List<Association> findRowsByIds(List<RelationRowId> rowIds) {
        List<Long> associationIds = rowIds == null
                ? List.of()
                : rowIds.stream().filter(rowId -> rowId.relationType() == RelationType.ASSOCIATION)
                        .map(RelationRowId::rowId).toList();
        return findByIds(associationIds);
    }

    /**
     * 批量保存（新增/修改按行存在性分派；规则三表同事务）。
     *
     * @param rows
     *            待保存聚合集合
     */
    @Override
    @Transactional
    public void save(List<Association> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<Long, AssociationContext> contexts = contextProvider.loadAll(rows);
        Set<Long> existingIds = existingIds(rows.stream().map(Association::associationId).toList());
        for (Association row : rows) {
            if (existingIds.contains(row.associationId())) {
                update(row, now);
            } else {
                insert(row, contexts.get(row.associationId()), now);
            }
        }
        AssociationRuleMapper.syncRules(jdbcTemplate, rows, contexts, now);
    }

    @Override
    @Transactional
    public void remove(List<Long> associationIds) {
        if (associationIds == null || associationIds.isEmpty()) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE " + AssociationStorage.ID + " IN ("
                + placeholders(associationIds.size()) + ")", associationIds.toArray());
        AssociationRuleMapper.removeRules(jdbcTemplate, associationIds);
    }

    private List<Association> attachRules(List<StoredRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> asLong(row.values().get(AssociationStorage.ID))).toList();
        Map<Long, com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationRule> rules = AssociationRuleMapper
                .loadRules(jdbcTemplate, ids);
        List<Association> result = new ArrayList<>(rows.size());
        for (StoredRow row : rows) {
            Long id = asLong(row.values().get(AssociationStorage.ID));
            result.add(AssociationMapper.reconstitute(row, rules.get(id)));
        }
        return result;
    }

    private Set<Long> existingIds(List<Long> associationIds) {
        if (associationIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(
                jdbcTemplate.queryForList(
                        "SELECT " + AssociationStorage.ID + " FROM " + TABLE + " WHERE " + AssociationStorage.ID
                                + " IN (" + placeholders(associationIds.size()) + ")",
                        Long.class, associationIds.toArray()));
    }

    private void insert(Association row, AssociationContext context, long now) {
        Map<String, Object> values = AssociationMapper.insertValues(row, context, now);
        String columns = String.join(",", values.keySet());
        String marks = placeholders(values.size());
        jdbcTemplate.update("INSERT INTO " + TABLE + " (" + columns + ") VALUES (" + marks + ")",
                values.values().toArray());
    }

    private void update(Association row, long now) {
        Map<String, Object> values = AssociationMapper.updateValues(row, now);
        Long id = (Long) values.remove(AssociationStorage.ID);
        String updateSet = String.join(",", values.keySet().stream().map(column -> column + " = ?").toList());
        Object[] args = new Object[values.size() + 1];
        int index = 0;
        for (Object value : values.values()) {
            args[index++] = value;
        }
        args[index] = id;
        jdbcTemplate.update("UPDATE " + TABLE + " SET " + updateSet + " WHERE " + AssociationStorage.ID + " = ?", args);
    }

    private List<StoredRow> query(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                values.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            return new StoredRow(values);
        }, args);
    }

    private static String placeholders(int size) {
        List<String> marks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            marks.add("?");
        }
        return String.join(",", marks);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }
}
