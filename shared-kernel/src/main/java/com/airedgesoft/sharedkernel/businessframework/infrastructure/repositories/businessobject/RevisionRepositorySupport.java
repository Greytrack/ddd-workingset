package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import com.airedgesoft.lib.id.Ids;
import com.airedgesoft.lib.jdbc.*;
import com.airedgesoft.lib.jdbc.AggregateMapping.*;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;
import com.airedgesoft.lib.jdbc.JdbcWritePlanner.Changes;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.ClassificationFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionFlags;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionNumber;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 多版次对象的写侧仓储支撑（v3 共表「指针版次」模型）：在 {@link BusinessObjectRepositorySupport} 的根级骨架上，
 * 收口版次级的<b>编排</b>——加载范围、出入库计划、版次动作翻译、实例级镜像、共享内核富文本表、索引/审计时机。
 *
 * <p>
 * <b>加载范围</b>：永不加载全部版次行，只取「根行 ＋ 指针版次行 ＋ 指针前一版次行」；更早历史由 {@link #findRevision} 定点重建。
 *
 * <p>
 * <b>版次动作只推导不猜测</b>：行的增删由「本次加载到的最大版次号」与「领域当前指针版次号」比大小推导（{@link RevisionOperation}），
 * 不从「加载快照 vs 领域终态」的集合差集猜动作；结论与本次读库快照交叉核验，动作已兑现时按幂等跳过（同一事务内重复保存常见）。
 *
 * <p>
 * <b>基类不负责</b>：表列声明、聚合重建/投影取值、业务属性与 apiId 的映射（富文本列名）、索引表协议、领域异常转译——都由子类钩子提供。
 *
 * @param <A>
 *            业务对象且可版次化的聚合
 * @param <ID>
 *            聚合身份
 */
public abstract class RevisionRepositorySupport<A extends BusinessObject & Revisionable, ID>
        extends
            BusinessObjectRepositorySupport<A, ID> {

    /** v3 共表版次协议（列名与类型键）：对所有 v3 共表 BC 一致，故在共享内核声明一次。 */
    public static final class Protocol {

        /** 版次行身份列。 */
        public static final String ID = "id";

        /** 根行上的版次指针列 / 版次行上的版次号列（同名 {@code revision}）。 */
        public static final String REVISION = "revision";

        /** 版次行指向实例的列。 */
        public static final String INSTANCE_ID = "instance_id";

        /** 工作集列（根行与版次行都存在，参与行定位）。 */
        public static final String WORKINGSET_ID = "workingset_id";

        /** 实例级签出态镜像列。 */
        public static final String CHECKED_OUT = "checked_out";

        /** 版次标记位（10 位，口径见 {@link RevisionFlags}）。 */
        public static final String FLAGS = "flags";

        /** 版次类型键（{@code VersionTypeListEnum}）。 */
        public static final String VERSION_TYPE = "version_type";

        public static final String VERSION = "version";

        public static final String MAIN_VERSION = "main_version";

        public static final String CHECKIN_TIME = "checkin_time";

        public static final String UPDATED_BY = "updated_by";

        public static final String UPDATED_TIME = "updated_time";

        /** 富文本内容表（版次列存富文本行 id，内容另存本表）。 */
        public static final String RICH_TEXT_TABLE = "air_inst_rich_text";

        /** v3 富文本主属性 {@code Content} 的固定落库列名（其余富文本属性按 apiId 小写对应）。 */
        public static final String RICH_TEXT_CONTENT_COLUMN = "description";

        /** v3 富文本属性 apiId 前缀（{@code RichText01} → {@code richtext01}）。 */
        public static final String RICH_TEXT_API_ID_PREFIX = "RichText";

        /** v3 富文本主属性的 apiId。 */
        public static final String CONTENT_API_ID = "Content";

        /** v3 基类列。 */
        public static final String BASE_CLASS = "base_class";

        /** 分类父类列。 */
        public static final String CLASS = "class";

        /** 分类类型列。 */
        public static final String TYPE = "type";

        /** 产品空间列。 */
        public static final String WORK_SPACE = "work_space";

        /** 创建人列。 */
        public static final String CREATED_BY = "created_by";

        /** 创建时间列。 */
        public static final String CREATED_TIME = "created_time";

        /** 乐观锁版本列。 */
        public static final String LOCK_VERSION = "lock_version";

        /** common 行身份列。 */
        public static final String COMMON_ID = "common_id";

        /** 版次行上的实例分类类型列。 */
        public static final String INSTANCE_TYPE = "instance_type";

        /** 小版本号列（v3 固定从 1 起）。 */
        public static final String SMALL_VERSION = "small_version";

        /** 前序版次指针列。 */
        public static final String LAST_REVISION_ID = "last_revision_id";

        /** 前序版本指针列。 */
        public static final String LAST_VERSION_ID = "last_version_id";

        /** 签出人列。 */
        public static final String CHECKOUT_USER = "checkout_user";

        /** 签出时间列。 */
        public static final String CHECKOUT_TIME = "checkout_time";

        /** 签入人列。 */
        public static final String CHECKIN_USER = "checkin_user";

        /** 首版小版本号（v3 {@code AddAction} 口径）。 */
        public static final int INITIAL_SMALL_VERSION = 1;

        /** 无用户上下文时的兼容账号（v3 {@code RequestThreadHolder} 空用户兜底）。 */
        public static final String NO_AUTH_ACTOR_ID = "419436436619264";

        /** 编号列。 */
        public static final String NUMBER = "number";

        /** 软删标记列。 */
        public static final String DELETE_FLAG = "delete_flag";

        /** 父实例列（v3 旧父边存版次行 id）。 */
        public static final String PARENT_ID = "parent_id";

        /** 追踪器列。 */
        public static final String TRACKER = "tracker";

        /**
         * 根行 INSERT 允许写的列（v3 共表协议）：身份 + 表协议列 + 根行专有协议列。
         *
         * <p>
         * common 业务列（name/status/owner）与 {@code max_revision} 不在其中——它们由 common 行 upsert 收口，避免两处写同一列。
         */
        public static final java.util.Set<String> ROOT_WRITE_COLUMNS = java.util.Set.of(ID, BASE_CLASS, CLASS, TYPE,
                WORK_SPACE, VERSION, WORKINGSET_ID, CREATED_BY, UPDATED_BY, COMMON_ID, LOCK_VERSION, CREATED_TIME,
                UPDATED_TIME, REVISION, DELETE_FLAG, CHECKED_OUT, PARENT_ID, TRACKER, NUMBER);



        /** 签出态列值。 */
        public static final int CHECKED_OUT_VALUE = 1;

        /** 签入态列值。 */
        public static final int CHECKED_IN_VALUE = 0;

        /** 有效版本类型键（{@code VersionTypeListEnum.VALID_VERSION}）：签入后当前行与所有新建行。 */
        public static final long VALID_VERSION_TYPE = 100472L;

        /** 工作版本类型键（{@code VersionTypeListEnum.WORKING_VERSION}）：签出工作版次唯一例外。 */
        public static final long WORKING_VERSION_TYPE = 100473L;

        /** NoVersion 基类的 {@code version}/{@code main_version} 落库值（v3 getCommonSql 口径，空串而非 NULL）。 */
        public static final String NO_VERSION = "";

        private Protocol() {
        }
    }

    /** 签出态列值。 */
    protected static final int CHECKED_OUT_VALUE = Protocol.CHECKED_OUT_VALUE;

    /** 签入态列值。 */
    protected static final int CHECKED_IN_VALUE = Protocol.CHECKED_IN_VALUE;

    /** 有效版本类型键。 */
    protected static final long VALID_VERSION_TYPE = Protocol.VALID_VERSION_TYPE;

    /** 工作版本类型键。 */
    protected static final long WORKING_VERSION_TYPE = Protocol.WORKING_VERSION_TYPE;

    /** NoVersion 基类的 {@code version}/{@code main_version} 落库值。 */
    protected static final String NO_VERSION = Protocol.NO_VERSION;

    /**
     * 版次结构操作（DR-1/DR-2）：只表达「加一行 / 删一行」，内容与协议列另算。
     */
    protected enum RevisionOperation {
        /** 无结构变化（新建、纯内容修改、签入、软删）。 */
        NONE,
        /** 指针前移到新工作版次（签出）：复制出新的版次行。 */
        ADD_REVISION,
        /** 指针回退到旧版次（取消签出）：删除高于指针的版次行。 */
        REMOVE_REVISION
    }

    /** 本次保存推导出的结构操作（聚合 id → 操作），供写后补写使用。 */
    private final Map<Long, RevisionOperation> pendingRevisionOperations = new ConcurrentHashMap<>();

    /** 更新路径待写富文本（聚合 id → 版次行 id ＋ 内容 ＋ 富文本行 id）。 */
    private final Map<Long, PendingRichTextWrite> pendingRichTextWrites = new ConcurrentHashMap<>();

    /** 取消签出待清退的富文本（聚合 id → 被丢弃版次行 id）。 */
    private final Map<Long, Long> pendingRichTextDelete = new ConcurrentHashMap<>();

    /** 计划期预分配的富文本行 id（聚合 id → apiId → 富文本行 id），落库后消费并清除。 */
    private final Map<Long, Map<String, Long>> pendingRichTextIds = new ConcurrentHashMap<>();

    /** 富文本内容行读写（v3 {@code air_inst_rich_text}）。 */
    private final AirInstRichTextStore richTextStore;

    protected RevisionRepositorySupport(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
            JdbcCommonInstanceStore commonInstances) {
        super(jdbcTemplate, transactionManager, commonInstances);
        this.richTextStore = new AirInstRichTextStore(jdbcTemplate);
    }

    // ==================== 子类必须声明 ====================

    /** 版次表声明（v3 共表 {@code air_inst_*_rev}）。 */
    protected abstract TableMapping revisionTable();

    /** 按本次读库快照重建聚合（当前版次＝指针行，历史基线＝指针前一版次行）。 */
    protected abstract A reconstruct(AggregateRows stored, Predicate<String> versionedTester);

    /** 定点重建指定版次的历史视角（只读：{@code current ≠ latest} 由领域保证不可写）。 */
    protected abstract Optional<A> reconstructAtRevision(AggregateRows stored, int revisionNumber,
            Predicate<String> versionedTester);

    /**
     * 首次保存投影（根行 ＋ 首版次行）。
     *
     * @param aggregate
     *            新聚合
     * @param richTextIdsByColumn
     *            富文本<b>列名 → 内容行 id</b>（已由基类按属性类型解析好，子类只按列落值）
     */
    protected abstract Projection<ID> projectNewState(A aggregate, Map<String, Long> richTextIdsByColumn);

    /**
     * 当前版次行的列值（富文本列按给定 id 落列：列存 id 不存内容）。
     *
     * @param aggregate
     *            聚合终态
     * @param revision
     *            待投影的版次记录
     * @param richTextIdsByColumn
     *            富文本列名 → 内容行 id
     * @return 列名 → 值
     */
    protected abstract Map<String, Value> currentRevisionValues(A aggregate, RevisionItem revision,
            Map<String, String> richTextIdsByColumn);

    /**
     * 组装比较投影：根行 ＋ 指针版次行 ＋（基线版次行原样投影，防差异计算误判为删除）。
     *
     * <p>
     * 行壳、身份列、工作集与锁版本全部由基类从本次读库快照造，子类只交「列名 → 值」。
     *
     * @param stored
     *            本次读库快照（提供身份/工作集/锁版本）
     * @param rootValues
     *            根行列值
     * @param currentRowValues
     *            指针版次行列值
     * @param baselineRowValues
     *            基线版次行列值（原样投影；可空）
     * @return 可直接参与差异比较的投影
     */
    protected final Projection<ID> assembleComparisonProjection(A aggregate, AggregateRows stored,
            Map<String, Value> rootValues, Map<String, Value> currentRowValues,
            Map<String, Value> baselineRowValues) {
        TableMapping root = rootTable();
        Row rootRow = RevisionRows.row(root, stored.root().longValue(Protocol.ID), null,
                stored.root().longValue(Protocol.WORKINGSET_ID), stored.root().longValue(root.versionColumn()),
                rootValues);
        TableMapping revision = revisionTable();
        int pointer = stored.children().stream()
                .mapToInt(row -> row.integerValue(Protocol.REVISION) == null ? 0 : row.integerValue(Protocol.REVISION))
                .max().orElse(0);
        RevisionItem current = aggregate.getCurrentRevisionItem();
        int currentRevisionNumber = current == null ? 0 : current.getRevisionNumber();
        Map<ID, Row> rows = new LinkedHashMap<>();
        for (StoredRow row : stored.children()) {
            Integer revisionNumber = row.integerValue(Protocol.REVISION);
            // 只投影「当前行」与「基线行」：其余存量行（如取消签出待丢弃的工作版次）不入投影，
            // 差异计算才能把它识别为「已移除的行」并生成带保护条件的 DELETE。
            RevisionItem baselineRevision = aggregate.getBaselineRevisionItem();
            Integer baselineNumber = baselineRevision == null ? null : baselineRevision.getRevisionNumber();
            // 只有「待丢弃的行」（版次号高于领域指针）从投影中缺席，并由 REMOVE_REVISION 显式 remove；
            // 其余存量行一律留在投影里（否则 lib-jdbc 视为隐式删除而拒绝）。
            Map<String, Value> values;
            if (revisionNumber != null && revisionNumber > currentRevisionNumber) {
                values = null;
            } else if (revisionNumber != null && revisionNumber == currentRevisionNumber) {
                values = currentRowValues;
            } else {
                values = baselineRowValues != null ? baselineRowValues : currentRowValues;
            }
            if (values == null) {
                continue;
            }
            Long rowId = row.longValue(revisionIdentityColumn());
            rows.put(asIdentity(rowId), RevisionRows.row(revision, rowId, stored.root().longValue(Protocol.ID),
                    row.longValue(Protocol.WORKINGSET_ID), row.longValue(revision.versionColumn()), values));
        }
        // 领域侧新增的版次行（签出）：尚未入库，按新身份与初始锁版本补进投影
        if (current != null && !rows.containsKey(asIdentity(revisionRowId(aggregate, current)))) {
            Long newRowId = revisionRowId(aggregate, current);
            rows.put(asIdentity(newRowId), RevisionRows.row(revision, newRowId,
                    stored.root().longValue(Protocol.ID), stored.root().longValue(Protocol.WORKINGSET_ID), 0L,
                    currentRowValues));
        }
        return new Projection<>(rootRow, rows);
    }

    /**
     * 根行列值：由子类声明（根表投影由 BC 拥有）。
     *
     * @param aggregate
     *            聚合
     * @return 根行列名 → 值
     */
    protected abstract Map<String, Value> rootValuesOf(A aggregate);

    /**
     * 基线版次行列值（取消签出的回退目标；无基线返回 {@code null} ⇒ 不入投影）。
     *
     * @param aggregate
     *            聚合
     * @return 列名 → 值；无基线为 {@code null}
     */
    protected abstract Map<String, Value> baselineValuesOf(A aggregate);

    /** 字段分桶判据（键＝落库列名）：versioned → 版次行，其余 → 共享桶。 */
    protected abstract Predicate<String> versionedTester(Long type);

    /**
     * v3 富文本<b>协议约定</b>下的 apiId → 列名换算（{@code Content} → {@code description}、{@code RichText01} → {@code richtext01}）。
     *
     * <p>
     * 只在元数据缺失/不齐全时作为兜底使用（见 {@link #richTextColumnOf}）：属性是否为富文本、列名是什么，
     * 优先以分类元数据为准。
     *
     * @param apiId
     *            属性 apiId
     * @return 落库列名；非富文本 apiId 返回 {@code null}
     */
    protected static String protocolRichTextColumnOf(String apiId) {
        if (apiId == null) {
            return null;
        }
        if (Protocol.CONTENT_API_ID.equals(apiId)) {
            return Protocol.RICH_TEXT_CONTENT_COLUMN;
        }
        return apiId.startsWith(Protocol.RICH_TEXT_API_ID_PREFIX) ? apiId.toLowerCase() : null;
    }

    /**
     * v3 富文本<b>协议约定</b>下的列名 → apiId 换算（{@link #protocolRichTextColumnOf} 的逆换算）。
     *
     * @param column
     *            落库列名
     * @return 属性 apiId；非富文本列返回 {@code null}
     */
    protected static String protocolRichTextApiIdOf(String column) {
        if (column == null) {
            return null;
        }
        if (Protocol.RICH_TEXT_CONTENT_COLUMN.equals(column)) {
            return Protocol.CONTENT_API_ID;
        }
        if (!column.startsWith("richtext")) {
            return null;
        }
        String suffix = column.substring("richtext".length());
        return suffix.isEmpty() ? null : Protocol.RICH_TEXT_API_ID_PREFIX + suffix;
    }

    /**
     * 分类元数据视图（供分桶判定与富文本属性判定共用；无元数据环境返回 {@code null}）。
     *
     * @param type
     *            分类类型键
     * @return 分类属性事实；无元数据时为空
     */
    protected abstract ClassificationFacts classificationOf(Long type);

    /**
     * 富文本属性事实：<b>由元数据判定</b>——属性类型为 {@code RICH_TEXT} 才返回。
     *
     * @param type
     *            分类类型键
     * @param column
     *            落库列名
     * @return 属性事实；非富文本或元数据缺失时为空
     */
    protected final Optional<AttributeFacts> richTextAttributeOf(Long type, String column) {
        ClassificationFacts classification = classificationOf(type);
        if (classification == null || column == null) {
            return Optional.empty();
        }
        return classification.byColumn(column).filter(RevisionRepositorySupport::isRichTextAttribute);
    }

    /**
     * 落库列名 → 富文本属性 apiId。
     *
     * <p>
     * <b>判定顺序</b>：分类元数据（属性类型 {@code RICH_TEXT}）优先；元数据缺失或该列不在元数据里时，
     * 退回 v3 富文本协议换算（{@link #protocolRichTextApiIdOf}），保证无元数据环境仍按 id 落库而不是把内容写进列。
     *
     * @param type
     *            分类类型键
     * @param column
     *            落库列名
     * @return 富文本属性的 apiId；非富文本列返回 {@code null}
     */
    protected final String richTextApiIdOf(Long type, String column) {
        return richTextAttributeOf(type, column).map(AttributeFacts::apiId)
                .orElseGet(() -> protocolRichTextApiIdOf(column));
    }

    /**
     * 富文本属性 apiId → 落库列名（与 {@link #richTextApiIdOf} 同一判定顺序：元数据优先、协议兜底）。
     *
     * @param type
     *            分类类型键
     * @param apiId
     *            属性 apiId
     * @return 落库列名；非富文本 apiId 返回 {@code null}
     */
    protected final String richTextColumnOf(Long type, String apiId) {
        ClassificationFacts classification = classificationOf(type);
        if (classification != null && apiId != null) {
            Optional<AttributeFacts> attribute = classification.byApiId(apiId)
                    .filter(RevisionRepositorySupport::isRichTextAttribute);
            if (attribute.isPresent()) {
                return attribute.get().column();
            }
        }
        return protocolRichTextColumnOf(apiId);
    }

    /** 属性类型是否为富文本（v3 {@code AttributeTypeEnum.RICH_TEXT}）。 */
    private static boolean isRichTextAttribute(AttributeFacts facts) {
        Integer typeKey = facts.typeKey();
        return typeKey != null && typeKey.intValue() == AttributeTypeEnum.RICH_TEXT.getIntKey();
    }

    // ==================== 可选钩子 ====================

    /**
     * 富文本候选内容（apiId → 内容）：<b>公共装配</b>——聚合的共享字段与当前版次业务字段全量过一遍，按协议换算挑出富文本列。
     *
     * <p>
     * 已是内容行 id 的值跳过（内容行缺失时列里仍是 id，不能当成内容回写）。子类无需覆写。
     *
     * @param aggregate
     *            聚合终态
     * @return apiId → 内容；无富文本时为空
     */
    protected final Map<String, String> richTextContentByApiId(A aggregate) {
        Map<String, FieldValue> candidates = new LinkedHashMap<>(aggregate.shared());
        RevisionItem current = aggregate.getCurrentRevisionItem();
        if (current != null) {
            candidates.putAll(current.getBusinessData());
        }
        Map<String, String> contents = new LinkedHashMap<>();
        candidates.forEach((column, field) -> {
            if (field == null || field.value() == null) {
                return;
            }
            String apiId = richTextApiIdOf(aggregate.getType(), column);
            String text = field.value().toText();
            if (apiId == null || text == null || text.isBlank() || text.matches("\\d+")) {
                return;
            }
            contents.put(apiId, text);
        });
        return contents;
    }

    /** 新建实例的索引补写（v3 工作集索引等 BC 协议）；默认无。 */
    protected void afterInstanceCreated(A aggregate, Long operatorId, long now) {
        // 默认无
    }

    /** 版次动作的索引补写；默认无。 */
    protected void afterRevisionAction(A aggregate, RevisionOperation operation, Long operatorId, long now) {
        // 默认无
    }

    /** 新工作版次的 {@code version} 列值（默认沿用源行；空则 NoVersion 口径空串）。 */
    protected String newWorkingVersion(StoredRow source) {
        String version = source.textValue(Protocol.VERSION);
        return version == null || version.isBlank() ? NO_VERSION : version;
    }

    /** 新工作版次的 {@code main_version} 列值（默认沿用源行）。 */
    protected String newWorkingMainVersion(StoredRow source) {
        String mainVersion = source.textValue(Protocol.MAIN_VERSION);
        return mainVersion == null ? NO_VERSION : mainVersion;
    }

    // ==================== 加载 ====================

    /**
     * 批量读取实际存在的根行及其版次行（指针版次 ＋ 指针前一版次），按实例归组。
     *
     * <p>
     * 与版次总数解耦：更早的历史行不加载（写路径不需要，读路径定点取）。
     */
    protected final List<AggregateRows> loadRows(Collection<ID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        TableMapping root = rootTable();
        List<StoredRow> roots = store().findAll(root, root.identityColumns().get(0), longIds(ids));
        if (roots.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> pointers = new HashMap<>();
        roots.forEach(row -> pointers.put(row.longValue(root.identityColumns().get(0)),
                row.integerValue(Protocol.REVISION)));
        List<StoredRow> revisions = roots.stream()
                .map(row -> row.longValue(root.identityColumns().get(0)))
                .flatMap(id -> store().findAll(revisionTable(), Protocol.INSTANCE_ID, List.of(id)).stream())
                .filter(row -> withinLoadingWindow(row, pointers.get(row.longValue(Protocol.INSTANCE_ID))))
                .toList();
        Map<Long, List<StoredRow>> grouped = new LinkedHashMap<>();
        revisions.forEach(row -> grouped.computeIfAbsent(row.longValue(Protocol.INSTANCE_ID), key -> new ArrayList<>())
                .add(row));
        return roots.stream().map(root2 -> new AggregateRows(root2,
                grouped.getOrDefault(root2.longValue(root.identityColumns().get(0)), List.of()))).toList();
    }

    /**
     * 加载并重建聚合（写事务内回读根版本，覆盖根与子行分次读取期间的并发写入）。
     *
     * @param stored
     *            原始快照
     * @return 重建后的聚合
     */
    protected final A loadAggregate(AggregateRows stored) {
        A aggregate = reconstruct(resolveRichTexts(stored),
                versionedTester(stored.root().longValue(typeColumn())));
        verifyRootVersionIfWriting(stored);
        return aggregate;
    }

    /**
     * 定点重建指定版次的历史视角；版次号未命中返回空（不进入写生命周期，不做根版本核验）。
     *
     * @param id
     *            聚合身份
     * @param revisionNumber
     *            目标版次号
     * @return 历史视角聚合
     */
    protected final Optional<A> findRevision(ID id, RevisionNumber revisionNumber) {
        if (id == null || revisionNumber == null) {
            return Optional.empty();
        }
        TableMapping root = rootTable();
        return store().findAll(root, root.identityColumns().get(0), longIds(List.of(id))).stream().findFirst()
                .flatMap(rootRow -> {
                    List<StoredRow> targetRows = store()
                            .findAll(revisionTable(), Protocol.INSTANCE_ID, longIds(List.of(id))).stream()
                            .filter(row -> row.integerValue(Protocol.REVISION) != null
                                    && row.integerValue(Protocol.REVISION) == revisionNumber.value())
                            .toList();
                    if (targetRows.isEmpty()) {
                        return Optional.empty();
                    }
                    return reconstructAtRevision(new AggregateRows(rootRow, targetRows), revisionNumber.value(),
                            versionedTester(rootRow.longValue(typeColumn())));
                });
    }

    // ==================== 保存计划 ====================

    /**
     * 首次保存计划：先为富文本列预分配内容行 id（列值与内容行同源），再交给子类投影。
     *
     * @param aggregate
     *            ID 已分配的新聚合
     * @param context
     *            调用期上下文（创建路径的审计列取聚合创建人，此处仅透传兼容 SAM）
     * @return 根行到版次行的有序插入计划
     */
    protected final Create planCreate(A aggregate, AggregateMapping.HookContext context) {
        Map<String, Long> richTextIds = allocateRichTextIds(aggregate);
        if (!richTextIds.isEmpty()) {
            pendingRichTextIds.put(aggregateIdOf(aggregate), richTextIds);
        }
        // 列 → id 的统一解析在这里完成（与更新路径同源），子类投影只按列名落值。
        return JdbcWritePlanner.create(projectNewState(aggregate, richTextIdsByColumn(aggregate.getType(), richTextIds)));
    }

    /**
     * 更新计划：重新读库 → 核验加载版本与根版本 → 按领域动作结论生成计划。
     *
     * @param aggregate
     *            动作后的聚合终态
     * @param loadedVersion
     *            事务内加载时记录的根锁版本（可为空＝存量 NULL）
     * @param context
     *            调用期上下文（操作人经此进入审计列）
     * @return 更新计划或无变化结果
     */
    protected final Plan planUpdate(A aggregate, Long loadedVersion, AggregateMapping.HookContext context) {
        AggregateRows current = loadRows(List.of(asIdentity(aggregateIdOf(aggregate)))).stream().findFirst()
                .orElseThrow(() -> versionConflict(aggregate, loadedVersion));
        assertLoadedVersion(aggregate, current, loadedVersion);
        return planChanges(aggregate, resolveRichTexts(current),
                versionedTester(current.root().longValue(typeColumn())), context.operatorId());
    }

    /**
     * 生成更新计划：普通列差异交给引擎，版次行列值与增删由本类按领域结论翻译。
     *
     * @param aggregate
     *            动作后的聚合终态
     * @param stored
     *            本次读库快照（富文本已解析为内容）
     * @return 计划
     */
    protected final Plan planChanges(A aggregate, AggregateRows stored) {
        return planChanges(aggregate, stored, versionedTester(stored.root().longValue(typeColumn())), null);
    }

    // ==================== 附加写入（模板固定） ====================

    /**
     * 写后补写：实例级 {@code checked_out} 镜像 → 创建口径索引/富文本 → 更新口径版次动作/富文本。
     *
     * <p>
     * 时序在此固定，子类只能通过 {@link #afterInstanceCreated} / {@link #afterRevisionAction} 声明索引协议，不能改编排。
     */
    @Override
    protected final void afterApplied(A aggregate, JdbcAggregateStore.Receipt.Applied applied, Long operatorId,
            long now) {
        mirrorInstanceCheckedOut(aggregate);
        if (applied.kind() == JdbcAggregateStore.Receipt.Kind.CREATED) {
            afterInstanceCreated(aggregate, operatorId, now);
            writeCreatedRichTextContents(aggregate, operatorId, now);
            return;
        }
        afterRevisionAction(aggregate, pendingRevisionOperations.getOrDefault(aggregateIdOf(aggregate),
                RevisionOperation.NONE), operatorId, now);
        writePendingRichTextChanges(aggregate, operatorId, now);
    }

    /**
     * 实例级 {@code checked_out} 镜像（v3 {@code RevisionDataObject} 口径）：本实例所有版次行取当前版次的签出态。
     *
     * <p>
     * 逐行比对无法表达「历史行也要跟着翻」——工作版次只需一行真实变更，其余行是镜像结果，故用一条实例级 UPDATE 收口。
     */
    protected final void mirrorInstanceCheckedOut(A aggregate) {
        RevisionItem current = aggregate.getCurrentRevisionItem();
        if (current == null) {
            return;
        }
        int checkedOut = current.getRevision().isCheckedOut() ? CHECKED_OUT_VALUE : CHECKED_IN_VALUE;
        jdbc().update("UPDATE " + revisionTable().table().sqlName() + " SET " + Protocol.CHECKED_OUT
                + " = ? WHERE " + Protocol.INSTANCE_ID + " = ?", checkedOut, aggregate.getId());
    }

    // ==================== 内部：计划 ====================

    private Plan planChanges(A aggregate, AggregateRows stored, Predicate<String> isVersioned, Long operatorId) {
        // 先推导本次结构操作（DR-1），后续富文本 id 定稿与协议列写入都依赖它
        RevisionOperation operation = deriveRevisionOperation(aggregate, stored);
        pendingRevisionOperations.put(aggregateIdOf(aggregate), operation);
        // 富文本列存 id：写计划期定稿 id（复用既有行 / 签出分配新行），内容行由 afterApplied 落库。
        RichTextValues richTexts = planRichTextValues(aggregate, operation);
        // before 侧富文本取库里旧 id、after 侧取本次要写的 id：换 id 的 UPDATE 才会生成。
        A before = reconstruct(stored, isVersioned);
        Projection<ID> beforeProjection = assembleComparisonProjection(before, stored, rootValuesOf(before),
                currentRevisionValues(before, before.getCurrentRevisionItem(), richTexts.storedIdsByColumn()),
                baselineValuesOf(before));
        Projection<ID> afterProjection = assembleComparisonProjection(aggregate, stored, rootValuesOf(aggregate),
                currentRevisionValues(aggregate, aggregate.getCurrentRevisionItem(), richTexts.plannedIdsByColumn()),
                baselineValuesOf(aggregate));
        Changes<ID> changes = JdbcWritePlanner.between(beforeProjection, afterProjection,
                java.util.Set.of(Protocol.UPDATED_BY, Protocol.UPDATED_TIME));
        contributeRevisionChanges(aggregate, stored, changes);
        return changes.finish(() -> auditValues(operatorId));
    }

    /**
     * 翻译领域交出的版次动作结论：普通字段差异已由引擎收集，这里只登记「哪一行的增删、哪几列协议值由动作决定」。
     */
    /**
     * 把推导出的结构操作翻译进写入计划；协议列一律按「每行自身状态」产出（DR-2），重复保存天然幂等。
     */
    private void contributeRevisionChanges(A aggregate, AggregateRows stored, Changes<ID> changes) {
        RevisionItem current = aggregate.getCurrentRevisionItem();
        if (current == null || stored.children().isEmpty()) {
            return;
        }
        RevisionOperation operation = pendingRevisionOperations.getOrDefault(aggregateIdOf(aggregate),
                RevisionOperation.NONE);
        Map<Long, StoredRow> physical = new HashMap<>();
        stored.children().forEach(row -> physical.put(row.longValue(revisionIdentityColumn()), row));
        switch (operation) {
        case ADD_REVISION -> openWorkingRevision(aggregate, physical, changes);
        case REMOVE_REVISION -> discardWorkingRevision(aggregate, physical, changes);
        case NONE -> writeProtocolColumnsByState(aggregate, physical, changes);
        }
    }

    /** 签出：从库内指针行复制出工作版次行，源行让出「最新版次」一位。 */
    private void openWorkingRevision(A aggregate, Map<Long, StoredRow> physical, Changes<ID> changes) {
        RevisionItem working = aggregate.getCurrentRevisionItem();
        Long workingId = revisionRowId(aggregate, working);
        StoredRow source = physical.values().stream()
                .max(java.util.Comparator.comparingInt(row -> revisionValueOf(row)))
                .orElseThrow(() -> new IllegalStateException("签出必须有库内指针行作为复制源"));
        if (physical.containsKey(workingId)) {
            return;
        }
        TableMapping table = revisionTable();
        Map<String, Value> overrides = new LinkedHashMap<>();
        overrides.put(Protocol.REVISION, table.value(Protocol.REVISION, working.getRevisionNumber()));
        overrides.put(Protocol.VERSION_TYPE, table.value(Protocol.VERSION_TYPE, WORKING_VERSION_TYPE));
        overrides.put(Protocol.VERSION, table.value(Protocol.VERSION, newWorkingVersion(source)));
        overrides.put(Protocol.MAIN_VERSION, table.value(Protocol.MAIN_VERSION, newWorkingMainVersion(source)));
        overrides.put(Protocol.FLAGS, table.value(Protocol.FLAGS, RevisionFlags.working()));
        overrides.put(Protocol.CHECKED_OUT, table.value(Protocol.CHECKED_OUT, CHECKED_OUT_VALUE));
        retargetCloneColumns(source, overrides);
        changes.copy(asIdentity(source.longValue(revisionIdentityColumn())), asIdentity(workingId), overrides);
        setProtocolValue(changes, source.longValue(revisionIdentityColumn()), Protocol.FLAGS,
                RevisionFlags.released(source.textValue(Protocol.FLAGS)));
    }

    /** 取消签出：删除高于指针的版次行（带保护条件），回退行与其余行按自身状态重写协议列。 */
    private void discardWorkingRevision(A aggregate, Map<Long, StoredRow> physical, Changes<ID> changes) {
        int pointer = aggregate.getCurrentRevisionItem().getRevisionNumber();
        physical.values().stream().filter(row -> revisionValueOf(row) > pointer).forEach(row -> {
            Long rowId = row.longValue(revisionIdentityColumn());
            changes.remove(asIdentity(rowId), removalProtections(row));
            pendingRichTextDelete.put(aggregateIdOf(aggregate), rowId);
        });
        writeProtocolColumnsByState(aggregate, physical, changes);
    }

    /**
     * 按每行自身状态写协议列（DR-2）：签出态就是工作版次（工作版本键），否则就是签入后的当前行（有效版本键）； 其余已加载行让出「最新签入版次 / 最新版次」。
     */
    private void writeProtocolColumnsByState(A aggregate, Map<Long, StoredRow> physical, Changes<ID> changes) {
        RevisionItem current = aggregate.getCurrentRevisionItem();
        if (current == null) {
            return;
        }
        Long currentRowId = revisionRowId(aggregate, current);
        boolean checkedOut = current.getRevision().isCheckedOut();
        setProtocolValue(changes, currentRowId, Protocol.FLAGS,
                checkedOut ? RevisionFlags.working() : RevisionFlags.checkedIn());
        setProtocolValue(changes, currentRowId, Protocol.VERSION_TYPE,
                checkedOut ? WORKING_VERSION_TYPE : VALID_VERSION_TYPE);
        physical.values().stream().filter(row -> !row.longValue(revisionIdentityColumn()).equals(currentRowId))
                .forEach(row -> setProtocolValue(changes, row.longValue(revisionIdentityColumn()), Protocol.FLAGS,
                        RevisionFlags.superseded()));
    }

    /** 版次行的版次号（缺列按 0，历史脏行不至于打断推导）。 */
    private static int revisionValueOf(StoredRow row) {
        Integer revision = row.integerValue(Protocol.REVISION);
        return revision == null ? 0 : revision;
    }


    /** 新行必须与源行脱钩的协议列（默认无：{@code last_revision_id} 等由子类补充）。 */
    protected void retargetCloneColumns(StoredRow source, Map<String, Value> overrides) {
        // 默认无
    }

    /** 移除版次行时必须匹配的原值（默认沿用 v3 两列：签出态与签入时间，防并发覆盖）。 */
    protected Map<String, Value> removalProtections(StoredRow discarded) {
        TableMapping table = revisionTable();
        return Map.of(Protocol.CHECKED_OUT,
                table.value(Protocol.CHECKED_OUT, discarded.integerValue(Protocol.CHECKED_OUT)),
                Protocol.CHECKIN_TIME, table.value(Protocol.CHECKIN_TIME, discarded.longValue(Protocol.CHECKIN_TIME)));
    }

    private void setProtocolValue(Changes<ID> changes, Long rowId, String column, Object value) {
        if (rowId == null || !changes.after().rows().containsKey(asIdentity(rowId))) {
            return;
        }
        changes.set(asIdentity(rowId), revisionTable().value(column, value));
    }

    private Map<String, Value> auditValues(Long operatorId) {
        TableMapping table = rootTable();
        return Map.of(Protocol.UPDATED_BY, table.value(Protocol.UPDATED_BY, actorOf(operatorId)),
                Protocol.UPDATED_TIME, table.value(Protocol.UPDATED_TIME, System.currentTimeMillis()));
    }

    // ==================== 工作集与行归属（v3 共表协议） ====================

    /**
     * 生效工作集：NULL 视为默认工作集（v3 {@code workingSet.isDefault()} 口径）。
     *
     * @param aggregate
     *            聚合
     * @return 生效工作集 id
     */
    protected final long effectiveWorkingsetIdOf(A aggregate) {
        Long workingsetId = aggregate.getWorkingsetId();
        return workingsetId == null ? defaultWorkingsetId() : workingsetId;
    }

    /**
     * 校验版次行的归属与根行一致（{@code instance_id}/{@code common_id}/生效工作集三项都必须相等）。
     *
     * @param stored
     *            本次读库快照
     */
    protected final void requireRowOwnership(AggregateRows stored) {
        requireRowOwnership(stored, defaultWorkingsetId());
    }

    /**
     * 校验版次行归属（静态入口：供各 BC 的静态重建路径复用同一口径）。
     *
     * @param stored
     *            本次读库快照
     * @param defaultWorkingsetId
     *            默认工作集 id（NULL 工作集按它折算）
     */
    public static void requireRowOwnership(AggregateRows stored, long defaultWorkingsetId) {
        StoredRow root = stored.root();
        Long rootInstance = root.longValue(Protocol.ID);
        Long rootCommon = root.longValue(Protocol.COMMON_ID);
        long rootWorkingset = root.longValue(Protocol.WORKINGSET_ID) == null
                ? defaultWorkingsetId
                : root.longValue(Protocol.WORKINGSET_ID);
        for (StoredRow row : stored.children()) {
            long rowWorkingset = row.longValue(Protocol.WORKINGSET_ID) == null
                    ? defaultWorkingsetId
                    : row.longValue(Protocol.WORKINGSET_ID);
            if (!Objects.equals(row.longValue(Protocol.INSTANCE_ID), rootInstance)
                    || !Objects.equals(row.longValue(Protocol.COMMON_ID), rootCommon) || rowWorkingset != rootWorkingset) {
                throw new IllegalStateException("版次行归属与根行不一致: 版次行=" + row.longValue(Protocol.ID));
            }
        }
    }

    // ==================== 声明一致性守卫（算法通用，清单由子类给） ====================

    /**
     * 校验聚合里的字段落库列与仓储声明的列一致：声明是落库协议的 SSOT，字段改名必须同步声明。
     *
     * @param fieldsByColumn
     *            聚合侧字段（键＝字段自己的列名）
     * @param declaredColumns
     *            仓储声明的列名集合
     */
    public static void requireDeclaredColumns(Map<String, FieldValue> fieldsByColumn,
            java.util.Set<String> declaredColumns) {
        declaredColumns.forEach(column -> {
            FieldValue field = fieldsByColumn.get(column);
            if (field != null && !Objects.equals(field.column(), column)) {
                throw new IllegalStateException("字段落库列与仓储声明不一致: " + column);
            }
        });
    }

    /**
     * 已加载字段不得因缺席被隐式清空：库里该列有值、而聚合侧与共享字段都没有它 → 视为误清空。
     *
     * <p>
     * 「已加载」判据＝原行该列非 NULL：库里本就 NULL 的列从未被加载，缺席重建不算清空。
     *
     * @param revision
     *            当前版次
     * @param sharedFields
     *            聚合共享字段（同样计入已加载）
     * @param original
     *            原版次行
     * @param declaredColumns
     *            仓储声明的列名集合
     */
    public static void requireNoImplicitClear(RevisionItem revision, Map<String, FieldValue> sharedFields,
            StoredRow original, java.util.Set<String> declaredColumns) {
        if (revision == null || original == null) {
            return;
        }
        Map<String, FieldValue> versionedFields = revision.getBusinessData();
        declaredColumns.forEach(column -> {
            Object loaded = original.values().get(column);
            if (loaded == null) {
                return;
            }
            if (!versionedFields.containsKey(column) && !sharedFields.containsKey(column)) {
                throw new IllegalStateException("已加载字段不能通过缺席隐式清空: " + column);
            }
        });
    }

    // ==================== 内部：富文本 ====================

    /**
     * 推导版次结构操作（DR-1）：只比「库内最大版次号」与「领域指针版次号」，不看集合差集。
     *
     * @param aggregate
     *            领域终态
     * @param stored
     *            本次读库快照
     * @return 结构操作
     */
    private RevisionOperation deriveRevisionOperation(A aggregate, AggregateRows stored) {
        RevisionItem current = aggregate.getCurrentRevisionItem();
        if (current == null || stored.children().isEmpty()) {
            return RevisionOperation.NONE;
        }
        int loadedLatest = stored.children().stream()
                .mapToInt(row -> row.integerValue(Protocol.REVISION) == null ? 0 : row.integerValue(Protocol.REVISION))
                .max().orElse(0);
        if (current.getRevisionNumber() > loadedLatest) {
            return RevisionOperation.ADD_REVISION;
        }
        return current.getRevisionNumber() < loadedLatest ? RevisionOperation.REMOVE_REVISION : RevisionOperation.NONE;
    }

    /** 解析版次列里的富文本 id 为内容（仅「值纯数字且存在内容行」的列替换）。 */
    protected final AggregateRows resolveRichTexts(AggregateRows rows) {
        Long type = rows.root().longValue(typeColumn());
        Map<Long, Map<String, String>> contents = richTextStore.findByRevisionRowIds(rows.children().stream()
                .map(row -> row.longValue(revisionIdentityColumn())).toList());
        if (contents.isEmpty()) {
            return rows;
        }
        List<StoredRow> resolved = rows.children().stream().map(row -> {
            Map<String, String> byApiId = contents.get(row.longValue(revisionIdentityColumn()));
            if (byApiId == null || byApiId.isEmpty()) {
                return row;
            }
            Map<String, Object> values = new LinkedHashMap<>(row.values());
            byApiId.forEach((apiId, content) -> {
                String column = richTextColumnOf(type, apiId);
                Object stored = column == null ? null : values.get(column);
                if (stored != null && String.valueOf(stored).matches("\\d+")) {
                    values.put(column, content);
                }
            });
            return new StoredRow(values);
        }).toList();
        return new AggregateRows(rows.root(), resolved);
    }

    /** 为富文本候选分配内容行 id（创建路径：列值与内容行同源，写库后补内容）。 */
    private Map<String, Long> allocateRichTextIds(A aggregate) {
        Map<String, Long> ids = new LinkedHashMap<>();
        richTextContentByApiId(aggregate).forEach((apiId, content) -> {
            if (content != null && !content.isBlank() && !content.matches("\\d+")) {
                ids.put(apiId, Ids.nextId());
            }
        });
        return ids;
    }

    /**
     * 更新路径的富文本定稿：解析当前版次行应存的 id（复用或新分配），并登记待写内容。
     *
     * <p>
     * 签出：新工作版次行还没有内容行 → 必须分配新 id（共用复制源内容会让签出期编辑污染历史版次）； 签入/内容修改：复用该版次行既有 id，内容改写；取消签出：丢弃行的内容行随行清退。
     */
    private RichTextValues planRichTextValues(A aggregate, RevisionOperation operation) {
        boolean opening = operation == RevisionOperation.ADD_REVISION;
        Map<String, String> contents = richTextContentByApiId(aggregate);
        if (contents.isEmpty()) {
            return RichTextValues.none();
        }
        Long type = aggregate.getType();
        Map<String, Long> ids = new LinkedHashMap<>();
        Map<String, String> before = new LinkedHashMap<>();
        RevisionItem baseline = aggregate.getBaselineRevisionItem();
        RevisionItem current = aggregate.getCurrentRevisionItem();
        long lookupRow = opening && baseline != null ? revisionRowId(aggregate, baseline)
                : revisionRowId(aggregate, current);
        contents.forEach((apiId, content) -> {
            if (content == null || content.isBlank() || content.matches("\\d+")) {
                return;
            }
            Long existing = richTextStore.existingId(lookupRow, apiId);
            if (existing != null) {
                String column = richTextColumnOf(type, apiId);
                if (column != null) {
                    before.put(column, String.valueOf(existing));
                }
            }
            ids.put(apiId, opening || existing == null ? Ids.nextId() : existing);
        });
        if (ids.isEmpty()) {
            return RichTextValues.none();
        }
        pendingRichTextWrites.put(aggregateIdOf(aggregate),
                new PendingRichTextWrite(revisionRowId(aggregate, current), contents, ids));
        return new RichTextValues(before, richTextIdsByColumnAsText(aggregate.getType(), ids));
    }

    /** 写富文本内容行（创建路径：id 已写进版次列，内容行按同一 id 补）。 */
    private void writeCreatedRichTextContents(A aggregate, Long operatorId, long now) {
        Map<String, Long> ids = pendingRichTextIds.remove(aggregateIdOf(aggregate));
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<String, String> contents = richTextContentByApiId(aggregate);
        richTextStore.upsert(revisionTable().table().sqlName(),
                revisionRowId(aggregate, aggregate.getCurrentRevisionItem()), contents, ids, actorOf(operatorId), now);
    }

    /** 取消签出的内容行清退 ＋ 更新路径的内容落库。 */
    private void writePendingRichTextChanges(A aggregate, Long operatorId, long now) {
        Long discardedRowId = pendingRichTextDelete.remove(aggregateIdOf(aggregate));
        if (discardedRowId != null) {
            richTextStore.delete(discardedRowId);
        }
        PendingRichTextWrite write = pendingRichTextWrites.remove(aggregateIdOf(aggregate));
        if (write != null) {
            richTextStore.upsert(revisionTable().table().sqlName(), write.revisionRowId(), write.contentByApiId(),
                    write.idByApiId(), actorOf(operatorId), now);
        }
    }

    /** 富文本 id 按「落库列名」重排（创建路径投影按列落值）。 */
    private Map<String, Long> richTextIdsByColumn(Long type, Map<String, Long> idsByApiId) {
        Map<String, Long> idsByColumn = new LinkedHashMap<>();
        idsByApiId.forEach((apiId, id) -> {
            String column = richTextColumnOf(type, apiId);
            if (column != null) {
                idsByColumn.put(column, id);
            }
        });
        return idsByColumn;
    }

    /** 富文本 id 按「落库列名」重排并以文本表示（比较投影里列值是文本 id）。 */
    private Map<String, String> richTextIdsByColumnAsText(Long type, Map<String, Long> idsByApiId) {
        Map<String, String> idsByColumn = new LinkedHashMap<>();
        idsByApiId.forEach((apiId, id) -> {
            String column = richTextColumnOf(type, apiId);
            if (column != null) {
                idsByColumn.put(column, String.valueOf(id));
            }
        });
        return idsByColumn;
    }

    /**
     * 富文本在比较投影两端的列值视图。
     *
     * @param storedIdsByColumn
     *            库里已有 id（列名 → id）：比较基准，缺它则「换 id」的 UPDATE 不会生成
     * @param plannedIdsByColumn
     *            本次要写的 id（列名 → id）
     */
    private record RichTextValues(Map<String, String> storedIdsByColumn, Map<String, String> plannedIdsByColumn) {

        static RichTextValues none() {
            return new RichTextValues(Map.of(), Map.of());
        }
    }

    /**
     * 更新路径待落库的富文本。
     *
     * @param revisionRowId
     *            版次行 id（内容表的 {@code db_table_id}）
     * @param contentByApiId
     *            apiId → 内容
     * @param idByApiId
     *            apiId → 内容行 id（与版次列里的值同源）
     */
    private record PendingRichTextWrite(long revisionRowId, Map<String, String> contentByApiId,
            Map<String, Long> idByApiId) {
    }

    // ==================== 内部：通用判定 ====================

    /** 加载窗口：指针版次行与指针前一版次行（取消签出回退基线）。 */
    private static boolean withinLoadingWindow(StoredRow row, Integer pointer) {
        Integer revision = row.integerValue(Protocol.REVISION);
        if (revision == null) {
            return false;
        }
        return pointer == null || revision.equals(pointer) || revision.equals(pointer - 1);
    }

    /** 版次行身份列 ＝ 根表身份列（v3 共表：{@code id}）。 */
    protected final String revisionIdentityColumn() {
        return rootTable().identityColumns().get(0);
    }

    /** 版次行 id：首版次行 id ＝ 实例 id（v3 既有事实），其余取版次自身 id。 */
    protected final long revisionRowId(A aggregate, RevisionItem item) {
        if (item == null) {
            throw new IllegalStateException("版次动作缺少版次行");
        }
        return RevisionRows.rowIdOf(((Number) aggregate.getId()).longValue(), item);
    }

    /** 分桶判据所用的类型列（v3 共表 {@code type}）。 */
    protected String typeColumn() {
        return "type";
    }

    private void verifyRootVersionIfWriting(AggregateRows stored) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            verifyRootVersion(stored);
        }
    }

    private void assertLoadedVersion(A aggregate, AggregateRows current, Long loadedVersion) {
        long loaded = loadedVersion == null ? 0L : loadedVersion;
        Long storedVersion = current.root().longValue(rootTable().versionColumn());
        long currentVersion = storedVersion == null ? 0L : storedVersion;
        if (loaded != currentVersion) {
            throw versionConflict(aggregate, loaded);
        }
    }

    /**
     * 乐观锁冲突异常（读不到行与版本不一致同码：都是「本次事务的加载前提已失效」）。
     *
     * @param aggregate
     *            当前聚合
     * @param loadedVersion
     *            事务内加载版本
     * @return 待抛出异常（具体错误码由子类按自身结果码提供）
     */
    protected abstract RuntimeException versionConflict(A aggregate, Long loadedVersion);

    /** 聚合身份 → v3 共表身份列值（恒 BIGINT）。 */
    private static long aggregateIdOf(BusinessObject aggregate) {
        return ((Number) aggregate.getId()).longValue();
    }

    /** 版次行 id → 聚合身份：v3 共表身份列恒 BIGINT，故子类 ID 即 Long。 */
    @SuppressWarnings("unchecked")
    private ID asIdentity(long value) {
        return (ID) Long.valueOf(value);
    }

    private static List<Long> longIds(Collection<?> ids) {
        return ids.stream().filter(Objects::nonNull).map(id -> ((Number) id).longValue()).toList();
    }
}
