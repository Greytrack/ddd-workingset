package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.airedgesoft.sharedkernel.businessframework.domain.reference.Reference;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceRepository;
import com.airedgesoft.lib.jdbc.StoredRow;

import static com.airedgesoft.sharedkernel.businessframework.infrastructure.reference.ReferenceStorage.TABLE;
/**
 * 引用聚合仓储实现（Spring JDBC 直读直写现表 {@code air_inst_tab_reference}；继承传播怀疑父仓储——触发口径 8）。
 *
 * <p>
 * <b>写侧</b>：INSERT 新行（ADDED 记录 → {@code revision_in/flags} 初始化，对齐 v3 {@code AddAction#insertReference} 产物）、 UPDATE
 * 存活行（失效 → {@code revision_out=R}）、物理删除（取消签出丢弃，判据＝记录集空）；行与记录同事务（六-6）。
 *
 * <p>
 * <b>读侧</b>：历史求值在仓储内完成（口径 11，{@link ReferenceMapper#effectiveAt}）；「不变」由 flags 位图承载（v3 口径—— 签出位图由对象侧统一翻位，本仓储不重复写）。
 *
 * <p>
 * <b>宿主冗余列</b>（common_id/instance_type/work_space/...）：仓储按引用方上下文填充（构造期经 {@link SourceContext} 传入）， 不进领域。
 */
@Repository
public class ReferenceRepositoryImpl implements ReferenceRepository {

    /** 引用方上下文快照（填充 v3 冗余列用；应用服务创建行时传入）。 */
    public record SourceContext(Long commonId, Long instanceType, Long workSpace, Long workingsetId, Long tracker,
            int sourceRevision) {
    }

    private static final String SELECT_COLUMNS = """
            SELECT id, common_id, instance_id, instance_type, attribute_api_id, instance_revision_id, propagation_type,
                   downstream_merge_revision, downstream_changed, related_instance_id, related_common_id, related_type,
                   related_workingset_id, related_revision_id, upstream_changed, reference_type, upstream_merge_revision,
                   tab_id, flags, revision_in, revision_out, edit_row, related_revision, work_space, related_work_space,
                   workingset_id, tracker, related_tracker, lock_version
            FROM air_inst_tab_reference
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ReferenceSourceContextProvider sourceContextProvider;

    /** 引用方上下文端口（应用层提供：按引用方 id 装配冗余列事实）。 */
    public interface ReferenceSourceContextProvider {

        /**
         * 装配引用方上下文。
         *
         * @param sourceId
         *            引用方需求 id
         * @return 上下文快照
         */
        SourceContext load(Long sourceId);
    }

    public ReferenceRepositoryImpl(JdbcTemplate jdbcTemplate, ReferenceSourceContextProvider sourceContextProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceContextProvider = sourceContextProvider;
    }

    // ==================== 查询 ====================

    @Override
    public List<Reference> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Object[] args = ids.toArray();
        List<StoredRow> rows = query(SELECT_COLUMNS + " WHERE id IN (" + placeholders + ")", args);
        return rows.stream().map(ReferenceMapper::reconstitute).toList();
    }

    @Override
    public List<Reference> findEffectiveRows(Long sourceId, String attributeApiId, int revision) {
        return findEffective(sourceId, revision, " AND attribute_api_id = ?", attributeApiId);
    }

    @Override
    public List<Reference> findEffectiveBySource(Long sourceId, int revision) {
        return findEffective(sourceId, revision, "", null);
    }

    private List<Reference> findEffective(Long sourceId, int revision, String extraFilter, Object extraArg) {
        // 口径 11：revision_in ≤ R 且 (revision_out=0 或 revision_out > R)——区间求值在仓储完成
        String sql = SELECT_COLUMNS
                + " WHERE instance_id = ? AND revision_in <= ? AND (revision_out = 0 OR revision_out IS NULL OR revision_out > ?)"
                + extraFilter;
        List<Object> args = new ArrayList<>(List.of(sourceId, (long) revision, (long) revision));
        if (extraArg != null) {
            args.add(extraArg);
        }
        return query(sql, args.toArray()).stream().map(ReferenceMapper::reconstitute).toList();
    }

    @Override
    public List<Reference> findUpstreamRows(Long upstreamObjectId) {
        // 该对象是被引用方（related_instance_id 侧）；只取仍有效的行
        return query(SELECT_COLUMNS + " WHERE related_instance_id = ? AND (revision_out = 0 OR revision_out IS NULL)",
                upstreamObjectId).stream().map(ReferenceMapper::reconstitute).toList();
    }

    @Override
    public List<Reference> findDownstreamRows(Long downstreamObjectId) {
        return query(SELECT_COLUMNS + " WHERE instance_id = ? AND (revision_out = 0 OR revision_out IS NULL)",
                downstreamObjectId).stream().map(ReferenceMapper::reconstitute).toList();
    }

    @Override
    public List<Reference> findRowsByIds(
            List<com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId> rowIds) {
        List<Long> referenceIds = rowIds.stream().filter(rowId -> rowId
                .relationType() == com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationType.REFERENCE)
                .map(com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId::rowId).toList();
        return findByIds(referenceIds);
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

    // ==================== 写入 ====================

    /**
     * 批量保存：按聚合态分派「新增行」（构造即新增记录）与「失效更新」（{@code revision_out=R}）。 行与记录同事务（{@code @Transactional}，六-6）。
     *
     * @param rows
     *            待保存聚合集合
     */
    @Override
    @Transactional
    public void save(List<Reference> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Reference row : rows) {
            if (exists(row.referenceId())) {
                updateInvalidation(row);
            } else {
                insertNew(row);
            }
        }
    }

    @Override
    @Transactional
    public void remove(List<Long> referenceIds) {
        if (referenceIds == null || referenceIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(referenceIds.size(), "?"));
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id IN (" + placeholders + ")", referenceIds.toArray());
    }

    private boolean exists(Long referenceId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(1) FROM " + TABLE + " WHERE id = ?", Integer.class,
                referenceId);
        return count != null && count > 0;
    }

    /** 新增行：v4 ADDED 记录 → v3 新增产物（revisionIn=R、revisionOut=0、flags[R]='1'；冗余列由上下文填充）。 */
    private void insertNew(Reference row) {
        SourceContext context = sourceContextProvider.load(row.sourceId());
        int revision = currentRevision(row);
        String flags = ReferenceMapper.setFlagAt(ReferenceMapper.emptyFlags(), revision, '1');
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " (id, common_id, instance_id, instance_type, attribute_api_id,"
                        + " instance_revision_id, propagation_type, downstream_merge_revision, downstream_changed,"
                        + " related_instance_id, related_common_id, related_type, related_workingset_id,"
                        + " related_revision_id, upstream_changed, reference_type, upstream_merge_revision, tab_id,"
                        + " flags, revision_in, revision_out, edit_row, related_revision, work_space,"
                        + " related_work_space, workingset_id, tracker, related_tracker, lock_version, created_by,"
                        + " created_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.referenceId(), context.commonId(), row.sourceId(), context.instanceType(), row.attributeApiId(),
                (long) context.sourceRevision(), row.propagationSuspect().propagationType(),
                row.propagationSuspect().downstreamMergeRevision(), 0L, row.targetId(), null, null, null,
                row.targetRevision().revision() == null ? null : (long) row.targetRevision().revision(), 0L,
                (long) row.referenceType().code(), row.propagationSuspect().upstreamMergeRevision(),
                ReferenceMapper.REFERENCE_TAB_ID, flags, (long) revision, 0L, 0L,
                row.targetRevision().revision() == null ? null : (long) row.targetRevision().revision(),
                context.workSpace(), null, context.workingsetId(), context.tracker(), null, 0L, null,
                System.currentTimeMillis());
    }

    /** 失效/怀疑更新：INVALIDATED 记录 → revision_out=R；怀疑状态 → upstream/downstream_changed 列（v3 同列）。 */
    private void updateInvalidation(Reference row) {
        Integer invalidated = row.revisionRecords().stream().filter(record -> !record.isEffective())
                .map(com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceRevisionRecord::revision)
                .findFirst().orElse(null);
        jdbcTemplate.update(
                "UPDATE " + TABLE + " SET revision_out = ?, upstream_changed = ?, downstream_changed = ? WHERE id = ?",
                invalidated == null ? null : (long) invalidated, row.propagationSuspect().upstreamChanged() ? 1L : 0L,
                row.propagationSuspect().downstreamChanged() ? 1L : 0L, row.referenceId());
    }

    private int currentRevision(Reference row) {
        return row.revisionRecords().stream().mapToInt(
                com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceRevisionRecord::revision).max()
                .orElse(1);
    }
}
