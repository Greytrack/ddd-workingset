package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 可排序树节点的表级只读 SQL（v3 共表协议；共享内核，任何版次化业务对象可复用）。
 *
 * <p>
 * <b>2026-11-22 归位（用户拍板）</b>：原 {@code infrastructure.providers.OrderTreeableRepositoryImpl} 不再 implements
 * {@code OrderTreeableRepository}——树端口由各 BC 聚合仓储（如 {@code RequirementRepositoryImpl}）亲身兑现；本类只保留「按表取树事实」的 SQL，
 * 因此降级为<b>无状态映射辅助</b>（同 {@code RequirementMapper} 口径：{@code final class} ＋ 静态方法 ＋ 包级可见），不挂 Spring / ORM
 * 注解、也不需要装配（{@code v4-architecture.md} §5.2：Mapper 不泄漏出本包）。
 *
 * <p>
 * <b>版次表名动态入参</b>（用户拍板）：{@code revisionTable} 由调用方传入，<b>不硬编码</b> {@code air_inst_requirement_rev}——同一套 v3 共表协议（版次行带
 * {@code parent_id} 父边、{@code rank} 位置值、{@code tracker} 根层分桶键）的任何版次表均可复用（第二消费方出现时再上移公共模块）。 共通表 {@code air_inst_common}
 * 固定：以 {@code c.revision = r.revision} 收窄到「根行指针版次」那一行，历史版次行不参与树。
 *
 * <p>
 * <b>只取 id</b>：容器成员与树结构一律返回对象 id（{@code List<Long>}／{@code int}），<b>不造成员视图 DTO</b>——子层／根层成员由调用方按 id
 * 加载领域对象（{@code findByIds}），位置值从领域对象读。
 *
 * <p>
 * <b>含已删除</b>：树结构与容器成员均返回已删除对象（墓碑占位，避免位置值被复用）；展示层过滤由调用方决定。
 *
 * <p>
 * <b>父边语义</b>：{@code parent_id} 存<b>父对象 id</b>（与 {@code air_inst_common.parent_id} 同值），因此树查询直接按对象 id 连接， 不依赖「首版次行 id
 * 等于对象 id」这一存储约定。
 */
public final class OrderTreeBusinessIds {

    /** 共通表：根行 + 指针版次（固定，不随版次表变化）。 */
    private static final String COMMON_TABLE = "air_inst_common";

    /** 表名口径：只允许小写标识符形态（表名拼进 SQL 标识符位、不做参数绑定，故先校验后拼接）。 */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    /** 当前版次行过滤：只有根行指针版次的版次行携带有效结构（{@code %s} ＝版次表名）。 */
    private static final String CURRENT_REVISION_JOIN_TEMPLATE = "FROM %s r JOIN " + COMMON_TABLE
            + " c ON c.id = r.instance_id AND c.revision = r.revision";

    /** 子层容器成员：父边定位。 */
    private static final String SELECT_CHILD_MEMBERS_SQL_TEMPLATE = "SELECT r.instance_id %s WHERE r.parent_id = ?";

    /** 一层子节点：父边等于起点（DISTINCT 去掉同一子节点的重复命中）。 */
    private static final String SELECT_CHILD_NODE_IDS_SQL_TEMPLATE = "SELECT DISTINCT r.instance_id %s WHERE r.parent_id = ?";

    /** 根层容器成员：父边为空、按 tracker 分桶。 */
    private static final String SELECT_ROOT_MEMBERS_SQL_TEMPLATE = """
            SELECT r.instance_id %s
            WHERE r.parent_id IS NULL
              AND r.tracker = ?
            """;

    /** 子树下沉：自起点按父边向下递归，depth 相对起点计（自身＝1）。 */
    private static final String SELECT_SUBTREE_SQL_TEMPLATE = """
            WITH RECURSIVE sub AS (
                SELECT c.id AS object_id, 1 AS depth
                FROM air_inst_common c
                WHERE c.id = ?
                UNION ALL
                SELECT child.id, sub.depth + 1
                FROM sub
                JOIN %s r ON r.parent_id = sub.object_id
                JOIN air_inst_common child ON child.id = r.instance_id AND child.revision = r.revision
                WHERE sub.depth < ?
            )
            SELECT object_id
            FROM sub
            ORDER BY depth
            """;

    /** 祖先链：自起点沿当前版次行的父边上溯，含起点自身。 */
    private static final String SELECT_ANCESTORS_SQL_TEMPLATE = """
            WITH RECURSIVE chain AS (
                SELECT c.id AS object_id, r.parent_id AS parent_id
                %s
                WHERE c.id = ?
                UNION ALL
                SELECT c.id, r.parent_id
                FROM chain
                JOIN air_inst_common c ON c.id = chain.parent_id
                JOIN %s r ON r.instance_id = c.id AND r.revision = c.revision
            )
            SELECT object_id
            FROM chain
            """;

    /** 子树高度：同一递归求最大 depth；递归上限仅用于防御脏数据成环，不表达业务层级上限。 */
    private static final String SELECT_SUBTREE_HEIGHT_SQL_TEMPLATE = """
            WITH RECURSIVE sub AS (
                SELECT c.id AS object_id, 1 AS depth
                FROM air_inst_common c
                WHERE c.id = ?
                UNION ALL
                SELECT child.id, sub.depth + 1
                FROM sub
                JOIN %s r ON r.parent_id = sub.object_id
                JOIN air_inst_common child ON child.id = r.instance_id AND child.revision = r.revision
                WHERE sub.depth < ?
            )
            SELECT COALESCE(MAX(depth), 0)
            FROM sub
            """;

    /** 递归防御上限：库内若存在脏环（A→B→A），递归需有界退出；业务层级上限由各 BC 守卫裁决，不在此表达。 */
    private static final int RECURSION_GUARD_DEPTH = 1000;

    private OrderTreeBusinessIds() {
    }

    /**
     * 同父兄弟 id（含已删除）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param parentId
     *            父对象 id；为空返回空集
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 子层成员对象 id
     */
    public static List<Long> findByParentId(JdbcTemplate jdbcTemplate, Long parentId, String revisionTable) {
        if (parentId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                SELECT_CHILD_MEMBERS_SQL_TEMPLATE.formatted(currentRevisionJoin(revisionTable)), Long.class, parentId);
    }

    /**
     * 根层成员 id（父边为空，按根层分桶键分桶；含已删除）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param rootBucketId
     *            根层分桶键（需求域＝跟踪分类 trackerId）；为空返回空集
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 根层成员对象 id
     */
    public static List<Long> findRootMembers(JdbcTemplate jdbcTemplate, Long rootBucketId, String revisionTable) {
        if (rootBucketId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(SELECT_ROOT_MEMBERS_SQL_TEMPLATE.formatted(currentRevisionJoin(revisionTable)),
                Long.class, rootBucketId);
    }

    /**
     * 子树 id（层序，父先于子，含起点自身）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param objectId
     *            子树根 id；为空返回空集
     * @param maxDepth
     *            最大层数（含自身）；非正数返回空集
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 层序 id 集合
     */
    public static List<Long> findSubtree(JdbcTemplate jdbcTemplate, Long objectId, int maxDepth, String revisionTable) {
        if (objectId == null || maxDepth < 1) {
            return List.of();
        }
        return jdbcTemplate.queryForList(SELECT_SUBTREE_SQL_TEMPLATE.formatted(revisionTable(revisionTable)),
                Long.class, objectId, maxDepth);
    }

    /**
     * 一层子节点 id（不含起点自身；含已删除）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param objectId
     *            父对象 id；为空返回空集
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 直接子节点 id 集合
     */
    public static List<Long> findChildIds(JdbcTemplate jdbcTemplate, Long objectId, String revisionTable) {
        if (objectId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
                SELECT_CHILD_NODE_IDS_SQL_TEMPLATE.formatted(currentRevisionJoin(revisionTable)), Long.class, objectId);
    }

    /**
     * 祖先链 id（自身起、向上到根，含起点自身；含已删除）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param objectId
     *            起点 id；为空返回空集
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 祖先链 id 集合（自身在前）
     */
    public static List<Long> findAncestors(JdbcTemplate jdbcTemplate, Long objectId, String revisionTable) {
        if (objectId == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList(SELECT_ANCESTORS_SQL_TEMPLATE.formatted(currentRevisionJoin(revisionTable),
                revisionTable(revisionTable)), Long.class, objectId);
    }

    /**
     * 子树高度（{@code objectId} 以下最大层数，自身＝1，无子＝1）。
     *
     * @param jdbcTemplate
     *            JDBC 执行模板
     * @param objectId
     *            起点 id；为空或不存在返回 0
     * @param revisionTable
     *            版次表名（动态入参，须为合法标识符）
     * @return 层数
     */
    public static int findSubtreeHeight(JdbcTemplate jdbcTemplate, Long objectId, String revisionTable) {
        if (objectId == null) {
            return 0;
        }
        Integer height = jdbcTemplate.queryForObject(
                SELECT_SUBTREE_HEIGHT_SQL_TEMPLATE.formatted(revisionTable(revisionTable)), Integer.class, objectId,
                RECURSION_GUARD_DEPTH);
        return height == null ? 0 : height;
    }

    /** 当前版次行过滤片段（{@code FROM 版次表 r JOIN air_inst_common c ON ...}）。 */
    private static String currentRevisionJoin(String revisionTable) {
        return CURRENT_REVISION_JOIN_TEMPLATE.formatted(revisionTable(revisionTable));
    }

    /** 版次表名校验：表名拼接进 SQL 标识符位（无参数绑定通道），非合法标识符一律拒绝，避免注入面。 */
    private static String revisionTable(String revisionTable) {
        if (revisionTable == null || !TABLE_NAME_PATTERN.matcher(revisionTable).matches()) {
            throw new IllegalArgumentException("非法版次表名：" + revisionTable);
        }
        return revisionTable;
    }
}
