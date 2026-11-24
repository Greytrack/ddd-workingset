package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionFlags;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 工作集索引写入（v3 共表 {@code air_ws_items_index} / {@code air_ws_items_revision_index}）：照 v3
 * {@code WorkingsetIndexService#addDataObjectIndex} + {@code buildItemIndex} / {@code buildItemVersionIndex} 口径。
 *
 * <p>
 * <b>为何必须写</b>：v3 的工作集树、列表、追踪视图全部依赖这两张索引表读数据；v4 只写 {@code air_inst_common} / {@code air_inst_requirement_rev} 而不写索引时，v3
 * 侧看不到 v4 创建的需求（数据在库里但不进索引 → 列表为空）。
 *
 * <p>
 * <b>取值口径</b>（与 v3 落库实测比对：{@code air_ws_items_index} 既有行）：
 *
 * <ul>
 * <li>{@code local_id}：默认工作集 = {@code common_id}（v3 {@code workingSet.isDefault()} 分支），非默认工作集 = 实例 id；
 * <li>{@code branch_id} = Tracker id；{@code parent_branch_id} = Tracker 的 {@code source_branch_id}（空间级 Tracker 为 NULL）；
 * <li>{@code master_id} = Tracker 分支链的根（无上源即自身）；
 * <li>创建即签入终态：{@code effective_flags=1110000000}、{@code flag1..3}=true、{@code flag4/flag5}=false、
 * {@code effective_checked_out=0}（v3 创建后 {@code checkIn} 再刷新的结果）。
 * </ul>
 *
 * <p>
 * 幂等：两表主键冲突即忽略（同一实例重复保存不产生第二行；v3 {@code findByLocalId} 命中时也只回填源指向）。
 */
public final class WorksetIndexStore<A extends BusinessObject & Revisionable> {

    /** 默认工作集 id（v3 {@code IWorkingset.DEFAULT_WORKINGSET_ID}）。 */
    private static final long DEFAULT_WORKINGSET_ID = 1L;

    /**
     * 创建即签入的生效标记（v3 {@code MainBitSetUtils} 5 位布尔视图：{@code T T T F F}）。
     *
     * <p>
     * 与版次行 flags 同源：取共享内核 {@code RevisionFlags} 的创建签入态，不在本类另写位串。
     */
    private static final String CHECKED_IN_FLAGS = RevisionFlags.created(false);

    /**
     * items_index 的生效列刷新（签出/签入/取消签出共用）：{@code effective_flags / effective_revision / effective_checked_out} 三项＋5 位布尔视图。
     *
     * <p>
     * v3 这三列恒等于「当前指针版次行」的对应值，故三项与布尔视图都从同一个位串解码，不另行推导。
     */
    private static final String UPDATE_ITEM_INDEX = """
            UPDATE air_ws_items_index
            SET effective_flags = ?, effective_revision = ?, effective_checked_out = ?,
                flag1 = ?, flag2 = ?, flag3 = ?, flag4 = ?, flag5 = ?, updated_by = ?, updated_time = ?
            WHERE common_id = ?
            """;

    /** 版次索引行的标记回写（签入后工作版次行翻成有效版本键）。 */
    private static final String UPDATE_REVISION_INDEX_FLAGS = """
            UPDATE air_ws_items_revision_index SET flags = ?, updated_by = ?, updated_time = ?
            WHERE id = ? AND common_id = ?
            """;

    /** 版次索引行新增（签出产生新工作版次行；取消签出时反向删除）。 */
    private static final String INSERT_REVISION_INDEX_ROW = """
            INSERT INTO air_ws_items_revision_index (id, work_space_id, workingset_id, master_id, branch_id, common_id,
                local_id, revision, flags, lock_version, created_by, created_time, updated_by, updated_time, sub_class_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String DELETE_REVISION_INDEX_ROW = """
            DELETE FROM air_ws_items_revision_index WHERE id = ? AND common_id = ?
            """;

    private static final String INSERT_ITEM_INDEX = """
            INSERT INTO air_ws_items_index (id, work_space_id, workingset_id, master_id, parent_branch_id, branch_id,
                common_id, local_id, parent_local_id, source_local_id, revision, has_copied, flags, lock_version,
                created_by, created_time, updated_by, updated_time, source_revision, sub_class_id, effective_revision,
                effective_flags, flag1, flag2, flag3, flag4, flag5, effective_checked_out)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0, 0, ?, ?, ?, ?, 1, ?, 1, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT (id) DO NOTHING
            """;

    private static final String INSERT_REVISION_INDEX = """
            INSERT INTO air_ws_items_revision_index (id, work_space_id, workingset_id, master_id, branch_id, common_id,
                local_id, revision, flags, lock_version, created_by, created_time, updated_by, updated_time, sub_class_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 1, 0, 0, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """;

    /** Tracker 分支事实（v3 {@code ITrackerInner} 的最小投影）。 */
    private record TrackerBranch(Long masterId, Long branchId, Long parentBranchId) {
    }

    private final JdbcTemplate jdbcTemplate;

    /** 版次表名（各 BC 自己的版次表，动态入参）。 */
    private final String revisionTableName;

    public WorksetIndexStore(JdbcTemplate jdbcTemplate, String revisionTableName) {
        this.jdbcTemplate = jdbcTemplate;
        this.revisionTableName = revisionTableName;
    }

    /**
     * 写入新实例的两张索引行（幂等）。
     *
     * @param requirement
     *            已落库的需求聚合（含实例 id / common id / 工作集 / 产品空间 / Tracker / 首版次 id）
     * @param indexId
     *            索引行主键（雪花 id）
     * @param now
     *            审计时间戳
     */
    public void writeNewInstance(A aggregate, long indexId, long now) {
        TrackerBranch branch = branchOf(aggregate.getTrackerId());
        if (branch == null) {
            // Tracker 元数据缺失（无 v3 元数据环境 / 单测）：不写索引，与 v3 isNeedInitIndex 不满足时同口径
            return;
        }
        long commonId = aggregate.getCommonId() == null ? aggregate.getId() : aggregate.getCommonId();
        boolean defaultWorkingset = aggregate.getWorkingsetId() == null
                || aggregate.getWorkingsetId() == DEFAULT_WORKINGSET_ID;
        long localId = defaultWorkingset ? commonId : aggregate.getId();
        String actor = RevisionRepositorySupport.Protocol.NO_AUTH_ACTOR_ID;
        // 首版次行 id = 实例 id（v3 rowIdOf 口径：revision==1 时版次行即根 id）；后续版次才用版次自身 id
        long revisionId = aggregate.getCurrentRevisionItem().getRevisionNumber() == 1
                ? aggregate.getId()
                : aggregate.getCurrentRevisionItem().getId();

        jdbcTemplate.update(INSERT_ITEM_INDEX, indexId, aggregate.getWorkSpace(),
                aggregate.getWorkingsetId() == null ? DEFAULT_WORKINGSET_ID : aggregate.getWorkingsetId(),
                branch.masterId(), branch.parentBranchId(), branch.branchId(), commonId, localId,
                aggregate.getParentId() == null ? null : parentLocalId(aggregate.getParentId()), localId, actor,
                now, actor, now, aggregate.getType(), CHECKED_IN_FLAGS, true, true, true, false, false);

        jdbcTemplate.update(INSERT_REVISION_INDEX, revisionId, aggregate.getWorkSpace(),
                aggregate.getWorkingsetId() == null ? DEFAULT_WORKINGSET_ID : aggregate.getWorkingsetId(),
                branch.masterId(), branch.branchId(), commonId, localId, actor, now, actor, now, aggregate.getType());
    }

    /**
     * 版次动作后刷新索引（签出 / 签入 / 取消签出共用一套口径）。
     *
     * <p>
     * <b>取数单源</b>：{@code effective_flags / effective_revision / effective_checked_out} 与 5 位布尔视图全部读自<b>当前指针版次行</b> ——
     * 与版次行的 {@code flags}/{@code revision}/{@code checked_out} 恒等，不在本类重算位串（重算就会与落库行分叉）。
     *
     * <p>
     * v3 实测终值：签出 {@code 0010000000 / chk=1 / rev=N+1 / F F T F F}；签入 {@code 1010000000 / chk=0 / rev=N+1 / T F T F F}；
     * 取消签出 {@code 1110000000 / chk=0 / rev=N / T T T F F}。
     *
     * @param requirement
     *            动作后的聚合（当前＝动作终态的指针版次）
     * @param newRevisionRow
     *            签出产生的新版次行 id（需补一条版次索引行）；其余动作为 {@code null}
     * @param removedRevisionRow
     *            取消签出丢弃的版次行 id（需删其版次索引行）；其余动作为 {@code null}
     * @param actor
     *            操作人列值
     * @param now
     *            审计时间戳
     */
    public void writeRevisionAction(A aggregate, Long newRevisionRow, Long removedRevisionRow, String actor,
            long now) {
        TrackerBranch branch = branchOf(aggregate.getTrackerId());
        if (branch == null) {
            // Tracker 元数据缺失（无 v3 元数据环境 / 单测）：不写索引，与 v3 isNeedInitIndex 不满足时同口径
            return;
        }
        RevisionFacts current = currentRevisionFacts(aggregate);
        if (current == null) {
            return;
        }
        long commonId = commonIdOf(aggregate);
        if (newRevisionRow != null) {
            insertRevisionIndex(aggregate, branch, commonId, newRevisionRow, current, actor, now);
        }
        if (removedRevisionRow != null) {
            jdbcTemplate.update(DELETE_REVISION_INDEX_ROW, removedRevisionRow, commonId);
        }
        jdbcTemplate.update(UPDATE_ITEM_INDEX, current.flags(), current.revision(), current.checkedOut(),
                bit(current.flags(), 0), bit(current.flags(), 1), bit(current.flags(), 2), bit(current.flags(), 3),
                bit(current.flags(), 4), actor, now, commonId);
        if (newRevisionRow == null && removedRevisionRow == null) {
            // 签入：已有版次索引行的标记随工作版次翻成签入态
            jdbcTemplate.update(UPDATE_REVISION_INDEX_FLAGS, current.flags(), actor, now, current.rowId(), commonId);
        }
    }

    /** 当前指针版次行的索引事实（{@code rowId} 用版次自身 id：首版次行 id = 实例 id）。 */
    private record RevisionFacts(long rowId, int revision, String flags, int checkedOut) {
    }

    /** 按领域当前版次号定位指针版次行，取索引所需三列；行不存在返回 {@code null}（不写索引）。 */
    private RevisionFacts currentRevisionFacts(A aggregate) {
        int revision = aggregate.getCurrentRevisionItem().getRevisionNumber();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, revision, flags, checked_out FROM " + revisionTableName
                        + " WHERE instance_id = ? AND revision = ?",
                aggregate.getId(), revision);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Long rowId = asLong(row.get("id"));
        Long checkedOut = asLong(row.get("checked_out"));
        return new RevisionFacts(rowId == null ? aggregate.getId() : rowId, revision,
                row.get("flags") == null ? null : row.get("flags").toString(),
                checkedOut == null ? 0 : checkedOut.intValue());
    }

    /** 位串第 {@code index} 位（0 基）是否为 1；长度不足按 0 处理（历史行缺位不阻断索引刷新）。 */
    private static boolean bit(String flags, int index) {
        return flags != null && flags.length() > index && flags.charAt(index) == '1';
    }

    /** 新增一条版次索引行（幂等：同一版次行重复保存不产生第二行）。 */
    private void insertRevisionIndex(A aggregate, TrackerBranch branch, long commonId, long revisionRowId,
            RevisionFacts facts, String actor, long now) {
        jdbcTemplate.update(INSERT_REVISION_INDEX_ROW, revisionRowId, aggregate.getWorkSpace(),
                workingsetIdOf(aggregate), branch.masterId(), branch.branchId(), commonId, localIdOf(aggregate),
                facts.revision(), facts.flags(), actor, now, actor, now, aggregate.getType());
    }

    private long commonIdOf(A aggregate) {
        return aggregate.getCommonId() == null ? aggregate.getId() : aggregate.getCommonId();
    }

    private long localIdOf(A aggregate) {
        boolean defaultWorkingset = aggregate.getWorkingsetId() == null
                || aggregate.getWorkingsetId() == DEFAULT_WORKINGSET_ID;
        return defaultWorkingset ? commonIdOf(aggregate) : aggregate.getId();
    }

    private long workingsetIdOf(A aggregate) {
        return aggregate.getWorkingsetId() == null ? DEFAULT_WORKINGSET_ID : aggregate.getWorkingsetId();
    }

    /** 父实例的索引 local_id（默认工作集按 common_id 定位；父边存的是父版次行 id，故先取其 common_id）。 */
    private Long parentLocalId(Long parentEdgeId) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT c.common_id FROM air_inst_requirement_rev r JOIN air_inst_common c ON c.id = r.instance_id "
                        + "WHERE r.id = ?",
                Long.class, parentEdgeId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Tracker 分支事实：{@code branch_id}=Tracker 自身，{@code parent_branch_id}=其上源分支， {@code master_id}=分支链根（无上源即自身）——与 v3
     * {@code initIndexContext} + {@code ITrackerInner#getMasterBranchId} 同口径。
     */
    private TrackerBranch branchOf(Long trackerId) {
        if (trackerId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, source_branch_id FROM air_ws_tracker WHERE id = ? AND delete_flag = 0", trackerId);
        if (rows.isEmpty()) {
            return null;
        }
        Long parent = asLong(rows.get(0).get("source_branch_id"));
        Long master = parent == null ? trackerId : rootOf(parent);
        return new TrackerBranch(master, trackerId, parent);
    }

    /** 沿 {@code source_branch_id} 上溯至根（防御环：最多 32 层，与 v3 构造器回退上限同口径）。 */
    private Long rootOf(Long branchId) {
        Long cursor = branchId;
        for (int depth = 0; cursor != null && depth < 32; depth++) {
            List<Long> parents = jdbcTemplate.queryForList(
                    "SELECT source_branch_id FROM air_ws_tracker WHERE id = ? AND delete_flag = 0", Long.class, cursor);
            if (parents.isEmpty() || parents.get(0) == null) {
                return cursor;
            }
            cursor = parents.get(0);
        }
        return cursor == null ? branchId : cursor;
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number number ? number.longValue() : Long.valueOf(value.toString().trim());
    }
}
