package com.airedgesoft.lib.jdbc;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.airedgesoft.lib.jdbc.JdbcWritePlan.*;

/**
 * 聚合持久化流程的身份与生命周期 hook；仓储自行决定如何重建对象和生成写入计划。 原始加载记录统一使用 {@link AggregateRows}，保留根行与子行的数据库原值。
 *
 * <p>
 * Builder 按 onLoad / onCreate / onUpdate 声明生命周期配置，build 后映射不可变；Store 按实例引用隔离事务内记录，查询与保存应复用同一映射实例。
 * 创建和更新回调返回计划；仓储可在回调中查询数据库，写入 SQL 由 Store 执行；事务协调和执行仍由 Store 负责。
 *
 * @param <A>
 *            领域聚合类型
 * @param <K>
 *            聚合身份类型；写入计划自行携带 SQL 物理定位
 */
public final class AggregateMapping<A, K> {
    /** 行投影携带的表元数据，供参数绑定、版本比较和保留列校验使用。 */
    public record TableMapping(Table table, Map<String, Column> columns, List<String> identityColumns,
            String versionColumn, Set<String> insertDefaults, Set<String> copyAlways) {
        /**
         * 冻结写入元数据并核验身份、版本、默认列及复制覆盖列不存在保留列冲突。
         *
         * @param table
         *            非空的受控数据库表
         * @param columns
         *            以列名为键的完整列定义，键必须与绑定列名一致
         * @param identityColumns
         *            非空且无重复的身份列名，必须已声明并与版本列分离
         * @param versionColumn
         *            已声明的 BIGINT 锁版本列名
         * @param insertDefaults
         *            INSERT 可省略并交给数据库默认值的普通列名
         * @param copyAlways
         *            复制时即使投影值相同也必须覆盖的普通列名
         */
        public TableMapping {
            columns = Collections.unmodifiableMap(new LinkedHashMap<>(columns));
            identityColumns = List.copyOf(identityColumns);
            insertDefaults = Set.copyOf(insertDefaults);
            copyAlways = Set.copyOf(copyAlways);
            columns.forEach((name, column) -> {
                if (!name.equals(column.name()))
                    throw new IllegalArgumentException("元数据列名不一致");
            });
            if (table == null || identityColumns.isEmpty()
                    || new HashSet<>(identityColumns).size() != identityColumns.size()
                    || !columns.keySet().containsAll(identityColumns) || !columns.containsKey(versionColumn)
                    || columns.get(versionColumn).jdbcType() != Types.BIGINT || identityColumns.contains(versionColumn)
                    || !columns.keySet().containsAll(insertDefaults) || !columns.keySet().containsAll(copyAlways)
                    || insertDefaults.contains(versionColumn) || !Collections.disjoint(identityColumns, insertDefaults)
                    || copyAlways.contains(versionColumn) || !Collections.disjoint(identityColumns, copyAlways)) {
                throw new IllegalArgumentException("表写入元数据不完整或保留列冲突");
            }
        }
        /**
         * 创建表写入声明，默认不省略插入列或强制复制覆盖。
         *
         * @param table
         *            符合受控标识符格式的表名
         * @param columns
         *            包含身份、锁版本和普通字段的完整列定义
         * @param identities
         *            非空且不重复的行身份列名
         * @param version
         *            已声明的 BIGINT 锁版本列名
         * @return 已校验且不可变的表声明
         */
        public static TableMapping of(String table, Map<String, Column> columns, List<String> identities,
                String version) {
            return new TableMapping(new Table(table), columns, identities, version, Set.of(), Set.of());
        }
        /**
         * 仅声明 INSERT 可省略的列，让数据库默认值生效；不把更新中的显式 NULL 当作省略。
         *
         * @param defaults
         *            允许在 INSERT 中缺席的普通列名，不能包含身份或版本列
         * @return 替换插入默认列配置后的新表声明
         */
        public TableMapping defaults(Set<String> defaults) {
            return new TableMapping(table, columns, identityColumns, versionColumn, defaults, copyAlways);
        }
        /**
         * 这些列即使比较值相同也要覆盖，避免 Clone 继承未经默认化的来源状态，尤其是需要重置为 NULL 的列。
         *
         * @param names
         *            复制时始终覆盖的普通列名，不能包含身份或版本列
         * @return 替换强制覆盖列配置后的新表声明
         */
        public TableMapping copyAlways(Set<String> names) {
            return new TableMapping(table, columns, identityColumns, versionColumn, insertDefaults, names);
        }
        /**
         * 按表声明创建列绑定值，并核验标量类型一致。
         *
         * @param name
         *            已在本表声明的列名
         * @param value
         *            与声明 JDBC 类型匹配的标量值；null 表示 SQL NULL
         * @return 带声明列类型的绑定值
         */
        public Value value(String name, Object value) {
            Column column = columns.get(name);
            if (column == null)
                throw new IllegalArgumentException("未声明的写入列: " + name);
            if (value != null && !switch (column.jdbcType()) {
                case Types.BIGINT -> value instanceof Long;
                case Types.INTEGER -> value instanceof Integer;
                case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR -> value instanceof String;
                case Types.SMALLINT -> value instanceof Short;
                case Types.REAL -> value instanceof Float;
                case Types.NUMERIC, Types.DECIMAL -> value instanceof BigDecimal;
                case Types.DATE -> value instanceof LocalDate;
                case Types.TIME -> value instanceof LocalTime;
                case Types.TIMESTAMP -> value instanceof LocalDateTime;
                case Types.TIMESTAMP_WITH_TIMEZONE -> value instanceof OffsetDateTime;
                // OTHER 在本库只表示 PostgreSQL UUID，不接收 jsonb 等驱动对象。
                case Types.OTHER -> value instanceof UUID;
                case Types.DOUBLE -> value instanceof Double;
                case Types.BOOLEAN -> value instanceof Boolean;
                default -> false;
            })
                throw new IllegalArgumentException("写入值与声明类型不一致: " + name);
            return new Value(column, value);
        }
        /**
         * 将物理身份、原始版本与字段投影分开封装，避免比较依据被字段终值替换。
         *
         * @param identity
         *            完整且只含声明身份列的定位值
         * @param version
         *            该行的原始锁版本；旧库允许 null，新建插入要求为 0
         * @param values
         *            本次用途所需的列值；新增时包含身份、初始版本及非默认列，更新时为普通字段
         * @return 绑定本表元数据的行投影
         */
        public Row row(Map<String, Value> identity, Long version, Map<String, Value> values) {
            if (!identity.keySet().equals(new HashSet<>(identityColumns))) {
                throw new IllegalArgumentException("行身份列与声明不一致");
            }
            check(identity);
            check(values);
            return new Row(this, new Target(table, identity), value(versionColumn, version), values);
        }
        /**
         * 核验列值的名称、类型与本表声明一致，拒绝未声明写入。
         *
         * @param values
         *            需要校验的身份、字段、审计或版本列绑定值
         */
        void check(Map<String, Value> values) {
            immutable(values).forEach((name, bound) -> {
                if (!value(name, bound.value()).equals(bound)) {
                    throw new IllegalArgumentException("绑定类型与元数据不一致: " + name);
                }
            });
        }
    }

    /** 定位条件和原始版本独立于待写值，避免领域默认化或字段修改改变 SQL 的比较依据。 */
    public record Row(TableMapping mapping, Target target, Value version, Map<String, Value> values) {
        /**
         * 冻结行投影并核验表、身份、版本和字段类型，保留独立定位依据。
         *
         * @param mapping
         *            该行所属表的完整写入声明
         * @param target
         *            只含声明身份列的物理行定位
         * @param version
         *            与表声明一致的原始锁版本绑定值
         * @param values
         *            用于比较或首次插入的列投影，按用途包含不同列范围
         */
        public Row {
            values = immutable(values);
            if (!mapping.table().equals(target.table())
                    || !target.predicates().keySet().equals(new HashSet<>(mapping.identityColumns()))
                    || !version.column().equals(mapping.columns().get(mapping.versionColumn()))) {
                throw new IllegalArgumentException("行元数据与身份或版本不一致");
            }
            mapping.check(target.predicates());
            mapping.check(values);
            mapping.value(version.column().name(), version.value());
        }
    }
    /** 比较投影可以包含领域默认值；不能用它替代保留原始 NULL 和旧协议值的存储快照。 */
    public record Projection<K>(Row root, Map<K, Row> rows) {
        /**
         * 冻结子行集合，分离领域比较投影与原始存储快照。
         *
         * @param root
         *            聚合根行的比较或插入投影
         * @param rows
         *            按稳定子行身份归组的子行投影，复制后保留迭代顺序
         */
        public Projection {
            rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
        }
    }
    /** 编译结果与执行回执分开：有计划不等于执行成功，执行成功也不等于外层事务已提交。 */
    public sealed interface Plan {
    }
    /** 没有净变化时跳过审计、CAS 和 DML，避免一次空保存也推进锁版本。 */
    public record NoChanges() implements Plan {
    }
    /** 新根尚不存在，按根到子行的顺序插入，无需竞争旧根版本。 */
    public record Create(long rootVersion, List<Operation> operations) implements Plan {
        /**
         * 冻结按根到子行排序的首次插入操作。
         *
         * @param rootVersion
         *            新根的初始锁版本，编译生成时为 0
         * @param operations
         *            依次插入根行和子行的操作列表
         */
        public Create {
            operations = List.copyOf(operations);
        }
    }
    /** 根 CAS 单独交给 Store 先执行；operations 只承载其后的行操作，避免根版本被重复推进。 */
    public record Update(Target root, Value expectedVersion, List<Operation> operations) implements Plan {
        /**
         * 冻结根 CAS 之后执行的行操作，不在操作列表中重复推进根版本。
         *
         * @param root
         *            根 CAS 使用的物理身份定位
         * @param expectedVersion
         *            加载时原始根版本，SQL NULL 保持原样参与比较
         * @param operations
         *            根 CAS 成功后按顺序执行的复制、更新和删除操作
         */
        public Update {
            operations = List.copyOf(operations);
        }
    }
    private final Function<A, K> identity;
    private final Function<AggregateRows, Long> storedVersion;
    private final TableMapping tableMapping;
    private final Function<AggregateRows, K> storedIdentity;
    private final Function<AggregateRows, A> load;
    private final CreatePlan<A> create;
    private final UpdatePlan<A> update;

    /**
     * 冻结身份和生命周期回调，不持有可变 Builder。
     *
     * @param builder
     *            已配置的身份、加载及可选写入 hook
     */
    private AggregateMapping(Builder<A, K> builder) {
        identity = Objects.requireNonNull(builder.identity, "聚合身份取值函数不能为空");
        storedIdentity = Objects.requireNonNull(builder.storedIdentity, "加载记录身份取值函数不能为空");
        load = Objects.requireNonNull(builder.load, "加载 hook 不能为空");
        storedVersion = builder.storedVersion;
        tableMapping = builder.tableMapping;
        create = builder.create;
        update = builder.update;
    }

    /**
     * 写计划钩子的调用期上下文：只在单次 {@code save} 调用内有效，随调用栈逐层传递（不用线程变量）。
     *
     * <p>
     * <b>lib-jdbc 不理解其业务含义</b>：本层只把它原样递给 {@code onCreate}/{@code onUpdate}； 谁是操作人、默认值如何兜底全部由仓储
     * 解释（与「不定义 LoadHooks/UpdateHooks、存储口径留在仓储」同口径）。
     *
     * @param operatorId
     *            本次写入的操作人 id；无请求上下文时为 {@code null}（兜底口径由仓储定）
     */
    public record HookContext(Long operatorId) {
        /** 无调用期上下文（兼容不传操作人的旧调用）。 */
        public static HookContext none() {
            return new HookContext(null);
        }
    }

    /** 新对象首次保存的计划钩子。 */
    @FunctionalInterface
    public interface CreatePlan<A> {
        Create apply(A aggregate, HookContext context);
    }

    /** 已有对象保存的计划钩子。 */
    @FunctionalInterface
    public interface UpdatePlan<A> {
        Plan apply(A aggregate, Long loadedVersion, HookContext context);
    }

    /**
     * 创建身份及生命周期配置草稿。
     *
     * @param <A>
     *            领域聚合类型
     * @param <K>
     *            聚合身份类型
     * @return 可配置三个直接回调的 Builder
     */
    public static <A, K> Builder<A, K> builder() {
        return new Builder<>();
    }

    /**
     * 复制配置以派生新映射，不复制事务记录。
     *
     * @return 独立的配置草稿
     */
    public Builder<A, K> toBuilder() {
        return new Builder<>(this);
    }

    /** 身份为公共配置；三个 hook 直接接收数据并返回结果，不要求使用内部子流程。 */
    public static final class Builder<A, K> {
        private Function<A, K> identity;
        private Function<AggregateRows, Long> storedVersion;
        private TableMapping tableMapping;
        private Function<AggregateRows, K> storedIdentity;
        private Function<AggregateRows, A> load;
        private CreatePlan<A> create;
        private UpdatePlan<A> update;

        /** 创建独立配置草稿。 */
        private Builder() {
        }

        /**
         * 复制函数引用，后续修改不影响原映射。
         *
         * @param mapping
         *            需要派生的原映射
         */
        private Builder(AggregateMapping<A, K> mapping) {
            identity = mapping.identity;
            storedVersion = mapping.storedVersion;
            storedIdentity = mapping.storedIdentity;
            load = mapping.load;
            create = mapping.create;
            update = mapping.update;
        }

        /**
         * 配置领域身份提取，用于事务对象复用与保存身份检查。
         *
         * @param identity
         *            从领域对象提取非空稳定身份的函数
         * @return 当前 Builder
         */
        public Builder<A, K> identity(Function<A, K> identity) {
            this.identity = Objects.requireNonNull(identity, "聚合身份取值函数不能为空");
            return this;
        }

        /**
         * 配置从原始快照提取锁版本（BIGINT 存储列原值）：Store 在事务上下文登记加载版本， 供新建/更新判定与更新 hook 的 CAS 前版本核验使用。领域对象不持有该技术字段。
         *
         * @param storedVersion
         *            从原始行快照提取存储锁版本的函数（可返回 null，旧库允许 NULL）
         * @return 当前 Builder
         */
        public Builder<A, K> storedVersion(Function<AggregateRows, Long> storedVersion) {
            this.storedVersion = Objects.requireNonNull(storedVersion, "存储版本取值函数不能为空");
            return this;
        }

        /**
         * 关联表映射（表结构 SSOT）：<b>锁版本列由表映射给出</b>——未显式配置 {@code storedVersion} 时，框架按表映射声明的版本列从根行读取， 业务无需为锁版本单独声明取值逻辑。
         *
         * @param tableMapping
         *            表映射（已声明版本列）
         */
        public Builder<A, K> tableMapping(TableMapping tableMapping) {
            this.tableMapping = Objects.requireNonNull(tableMapping, "表映射不能为空");
            return this;
        }

        /**
         * 配置原始快照身份提取，用于核对请求和重建对象身份。
         *
         * @param storedIdentity
         *            从原始快照提取非空身份的函数
         * @return 当前 Builder
         */
        public Builder<A, K> storedIdentity(Function<AggregateRows, K> storedIdentity) {
            this.storedIdentity = Objects.requireNonNull(storedIdentity, "加载记录身份取值函数不能为空");
            return this;
        }

        /**
         * 加载原始快照后的扩展点；仓储负责重建、业务归属及所需的加载一致性检查。
         *
         * @param load
         *            接收原始快照并返回领域对象的函数；缓存命中及保存时不调用
         * @return 当前 Builder
         */
        public Builder<A, K> onLoad(Function<AggregateRows, A> load) {
            this.load = Objects.requireNonNull(load, "加载 hook 不能为空");
            return this;
        }

        /**
         * 新对象首次保存的扩展点（不关心调用期上下文的简写）。
         *
         * @param create
         *            接收按 ID/锁版本判定的新对象并返回 Create 计划的函数
         * @return 当前 Builder
         */
        public Builder<A, K> onCreate(Function<A, Create> create) {
            Objects.requireNonNull(create, "创建 hook 不能为空");
            return onCreate((aggregate, context) -> create.apply(aggregate));
        }

        /**
         * 新对象首次保存的扩展点；仓储负责生成完整插入计划。
         *
         * @param create
         *            接收新对象与调用期上下文（含操作人）并返回 Create 计划的函数
         * @return 当前 Builder
         */
        public Builder<A, K> onCreate(CreatePlan<A> create) {
            this.create = Objects.requireNonNull(create, "创建 hook 不能为空");
            return this;
        }

        /**
         * 已有对象保存的扩展点（不关心调用期上下文的简写）。
         *
         * @param update
         *            接收领域终态，自行查询当前数据库状态并返回 Update 或 NoChanges 的函数
         * @return 当前 Builder
         */
        public Builder<A, K> onUpdate(BiFunction<A, Long, Plan> update) {
            Objects.requireNonNull(update, "更新 hook 不能为空");
            return onUpdate((aggregate, loadedVersion, context) -> update.apply(aggregate, loadedVersion));
        }

        /**
         * 已有对象保存的扩展点；仓储重新读库并核对对象版本，再生成更新计划。
         *
         * @param update
         *            接收领域终态、读库锁版本与调用期上下文（含操作人）的函数
         * @return 当前 Builder
         */
        public Builder<A, K> onUpdate(UpdatePlan<A> update) {
            this.update = Objects.requireNonNull(update, "更新 hook 不能为空");
            return this;
        }

        /**
         * 冻结配置；创建与更新只需配置各自使用的 hook。
         *
         * @return 可在查询与保存之间复用的不可变映射
         */
        public AggregateMapping<A, K> build() {
            return new AggregateMapping<>(this);
        }
    }

    /**
     * 调用创建 hook 生成新对象的插入计划。
     *
     * @param aggregate
     *            按 ID/锁版本判定的新对象
     * @return 仓储生成的非空插入计划
     */
    public Create createPlan(A aggregate) {
        return createPlan(aggregate, HookContext.none());
    }

    /**
     * 调用创建 hook 生成新对象的插入计划（带调用期上下文）。
     *
     * @param aggregate
     *            按 ID/锁版本判定的新对象
     * @param context
     *            调用期上下文（操作人等），由 {@code save} 透传
     * @return 仓储生成的非空插入计划
     */
    public Create createPlan(A aggregate, HookContext context) {
        if (create == null)
            throw new IllegalStateException("聚合未配置 onCreate");
        return Objects.requireNonNull(create.apply(aggregate, context), "创建 hook 不能返回空计划");
    }

    /**
     * 调用更新 hook；库不提供缓存快照，仓储负责重新读库并校验对象携带的旧版本。
     *
     * @param aggregate
     *            经过领域修改的已有对象
     * @return 仓储生成的 Update 或 NoChanges
     */
    public Plan updatePlan(A aggregate, Long loadedVersion) {
        return updatePlan(aggregate, loadedVersion, HookContext.none());
    }

    /**
     * 调用更新 hook（带调用期上下文）。
     *
     * @param aggregate
     *            经过领域修改的已有对象
     * @param loadedVersion
     *            本事务加载时记录的锁版本
     * @param context
     *            调用期上下文（操作人等）
     * @return 仓储生成的 Update 或 NoChanges
     */
    public Plan updatePlan(A aggregate, Long loadedVersion, HookContext context) {
        if (update == null)
            throw new IllegalStateException("聚合未配置 onUpdate");
        Plan plan = Objects.requireNonNull(update.apply(aggregate, loadedVersion, context), "更新 hook 不能返回空计划");
        if (plan instanceof Create)
            throw new IllegalStateException("onUpdate 不能返回创建计划");
        return plan;
    }

    /**
     * 从原始行快照提取存储锁版本：显式取值函数优先；未配置时按表映射声明的版本列从根行读取（表结构 SSOT，业务无需为锁版本单独声明）； 两者皆无 → null（该表无版本列）。
     */
    Long storedVersion(AggregateRows stored) {
        if (storedVersion != null) {
            return storedVersion.apply(stored);
        }
        if (tableMapping == null) {
            return null;
        }
        return stored.root().longValue(tableMapping.versionColumn());
    }

    /**
     * 提取领域身份，并拒绝空身份。
     *
     * @param aggregate
     *            需要获取身份的领域对象
     * @return 非空领域身份
     */
    K identity(A aggregate) {
        return Objects.requireNonNull(identity.apply(aggregate), "聚合身份不能为空");
    }

    /**
     * 提取原始快照身份，并拒绝空身份。
     *
     * @param stored
     *            原始加载快照
     * @return 非空存储身份
     */
    K storedIdentity(AggregateRows stored) {
        return Objects.requireNonNull(storedIdentity.apply(stored), "加载记录身份不能为空");
    }

    /**
     * 调用加载 hook 并核对身份，事务内登记由 Store 负责。
     *
     * @param stored
     *            加载到的原始根行与子行
     * @return 身份与快照一致的非空领域对象
     */
    A reconstruct(AggregateRows stored) {
        A aggregate = Objects.requireNonNull(load.apply(stored), "加载 hook 不能返回空对象");
        if (!Objects.equals(identity(aggregate), storedIdentity(stored)))
            throw new IllegalStateException("重建对象必须保留加载记录身份");
        return aggregate;
    }
}
