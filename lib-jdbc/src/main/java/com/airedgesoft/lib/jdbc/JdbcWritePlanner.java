package com.airedgesoft.lib.jdbc;

import static com.airedgesoft.lib.jdbc.AggregateMapping.*;
import static com.airedgesoft.lib.jdbc.JdbcWritePlan.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * 仓储可选使用的纯写入计划工具；不注册 hook，不重建领域对象，不访问数据库。 仓储也可以直接构造 Create / Update / NoChanges，而不使用本工具的比较及复制协议。
 */
public final class JdbcWritePlanner {
    /** 仅提供纯函数，不创建有状态工具实例。 */
    private JdbcWritePlanner() {
    }

    /**
     * 从调用方给出的前后投影建立可补充的行变化，不替调用方选择比较口径。
     *
     * @param <K>
     *            子行身份键类型
     * @param before
     *            调用方生成的修改前比较投影
     * @param after
     *            调用方生成的领域终态投影
     * @param auditColumns
     *            由调用方声明的更新审计列名
     * @return 可继续补充复制、删除及列终值的收集器
     */
    public static <K> Changes<K> between(Projection<K> before, Projection<K> after, Set<String> auditColumns) {
        return new Changes<>(before, after, auditColumns);
    }

    /**
     * 根据行自带元数据校验审计列和重复物理身份；不判断表选择与业务归属。
     *
     * @param <K>
     *            子行身份键类型
     * @param projection
     *            待编译的原始、当前或新建聚合行投影
     * @param auditColumns
     *            需校验兼容性的审计列名
     */
    private static <K> void validate(Projection<K> projection, Set<String> auditColumns) {
        List<Row> rows = new ArrayList<>();
        rows.add(projection.root());
        rows.addAll(projection.rows().values());
        Set<Target> identities = new HashSet<>();
        rows.forEach(row -> {
            TableMapping table = row.mapping();
            if (!table.columns().keySet().containsAll(auditColumns) || auditColumns.contains(table.versionColumn())
                    || !Collections.disjoint(auditColumns, table.identityColumns())) {
                throw new IllegalArgumentException("审计列与表声明不一致或占用保留列");
            }
            if (!identities.add(row.target()))
                throw new IllegalStateException("投影行物理身份重复");
        });
    }
    /**
     * 按根到子行顺序编译首次插入计划，无需竞争旧根版本。
     *
     * @param <K>
     *            子行身份键类型
     * @param projection
     *            包含全部新行身份、初始版本与插入值的新建投影
     * @return 已冻结操作顺序的新增计划
     */
    public static <K> Create create(Projection<K> projection) {
        validate(projection, Set.of());
        List<Operation> operations = new ArrayList<>();
        operations.add(insert(projection.root()));
        projection.rows().values().forEach(row -> operations.add(insert(row)));
        return new Create((Long) projection.root().version().value(), operations);
    }
    /**
     * 校验单行插入的列完整性、身份一致性及初始版本为 0。
     *
     * @param row
     *            包含身份列、零版本及全部非默认列的插入投影
     * @return 经过校验的单行插入操作
     */
    private static Insert insert(Row row) {
        Set<String> missing = new HashSet<>(row.mapping().columns().keySet());
        missing.removeAll(row.values().keySet());
        if (!row.mapping().insertDefaults().containsAll(missing)
                || !Objects.equals(row.values().get(row.version().column().name()), row.version())
                || !Long.valueOf(0L).equals(row.version().value())) {
            throw new IllegalStateException("插入列缺失或初始版本不为 0");
        }
        row.target().predicates().forEach((name, value) -> {
            if (!value.equals(row.values().get(name)))
                throw new IllegalStateException("插入身份与行定位不一致");
        });
        return new Insert(row.target().table(), row.values());
    }

    /** 可选的行变化收集器；由仓储选择何时比较、补充操作并生成最终计划。 */
    public static final class Changes<K> {
        private final Projection<K> before;
        private final Projection<K> after;
        private final Set<String> auditColumns;
        private final Map<K, JdbcRowChanges> updates = new LinkedHashMap<>();
        private final Map<K, Map<String, Value>> contributions = new LinkedHashMap<>();
        private final Map<K, Clone> copies = new LinkedHashMap<>();
        private final Map<K, Delete> removals = new LinkedHashMap<>();
        private final JdbcRowChanges root;

        /**
         * 计算根及仍存在子行的普通字段差异，为特殊贡献建立上下文。
         *
         * @param before
         *            由原始记录重建旧对象后生成的比较投影
         * @param after
         *            领域终态的比较投影，保留原始身份和版本
         * @param auditColumns
         *            保留给框架附加、禁止普通字段和贡献覆盖的审计列名
         */
        private Changes(Projection<K> before, Projection<K> after, Set<String> auditColumns) {
            validate(before, auditColumns);
            validate(after, auditColumns);
            this.before = before;
            this.after = after;
            this.auditColumns = Set.copyOf(auditColumns);
            root = difference(before.root(), after.root());
            after.rows().forEach((id, row) -> {
                Row original = before.rows().get(id);
                if (original != null)
                    updates.put(id, difference(original, row));
            });
        }
        /**
         * 提供旧对象的比较投影，供贡献判断来源与状态。
         *
         * @return 重建旧对象后生成的投影，可能已包含领域默认值
         */
        public Projection<K> before() {
            return before;
        }
        /**
         * 提供领域终态的比较投影，供贡献定位目标行。
         *
         * @return 待保存聚合的当前行投影
         */
        public Projection<K> after() {
            return after;
        }
        /**
         * 供贡献回调判断已有行的普通字段差异，不混入稍后附加的审计与锁版本。
         *
         * @param id
         *            在前后投影中都存在的子行身份
         * @return 当前已收集的普通变化列；没有对应更新行时为空，不含稍后附加的审计和版本
         */
        public Set<String> changedColumns(K id) {
            JdbcRowChanges row = updates.get(id);
            return row == null ? Set.of() : row.columnNames();
        }
        /**
         * 仅补充投影未拥有的协议列；已声明普通字段不能被覆盖，同一协议列的冲突终值也必须拒绝。
         *
         * @param id
         *            在前后投影中都存在的已加载子行身份
         * @param value
         *            已声明且不占用身份、版本或审计的列终值；与普通字段相同值可重复声明
         */
        public void set(K id, Value value) {
            Row row = after.rows().get(id);
            if (!updates.containsKey(id))
                throw new IllegalStateException("只能设置仍存在的已加载子行");
            permitted(row, Map.of(value.column().name(), value));
            String name = value.column().name();
            if (row.values().containsKey(name)) {
                if (!value.equals(row.values().get(name))) {
                    throw new IllegalStateException("特殊终值不能覆盖普通字段声明: " + name);
                }
                return;
            }
            Map<String, Value> assigned = contributions.computeIfAbsent(id, ignored -> new LinkedHashMap<>());
            merge(assigned, Map.of(name, value));
        }
        /**
         * 新子行必须有已加载的复制源，避免把集合新增误解为会丢失未装配列的普通 INSERT。
         *
         * @param sourceId
         *            修改前投影中已加载的复制来源身份
         * @param targetId
         *            仅在修改后投影中存在的目标子行身份，须与来源属于同一表声明
         * @param overrides
         *            额外覆盖的已声明普通列值，不得改写保留列或与目标普通投影冲突
         */
        public void copy(K sourceId, K targetId, Map<String, Value> overrides) {
            Row source = before.rows().get(sourceId);
            Row target = after.rows().get(targetId);
            if (source == null || target == null || before.rows().containsKey(targetId)
                    || !source.mapping().equals(target.mapping()) || copies.containsKey(targetId)) {
                throw new IllegalStateException("复制必须使用已加载来源与明确的新子行");
            }
            permitted(target, overrides);
            overrides.forEach((name, value) -> {
                if (target.values().containsKey(name) && !value.equals(target.values().get(name))) {
                    throw new IllegalStateException("复制覆盖不能修改普通字段终值: " + name);
                }
            });
            Map<String, Value> values = new LinkedHashMap<>(
                    JdbcRowChanges.difference(source.values(), target.values()));
            // 这些列即使与来源投影相等也必须覆盖；来源原始值可能已被领域默认化，且 NULL 也有重置含义。
            target.mapping().copyAlways().forEach(name -> {
                Value value = target.values().get(name);
                if (value == null)
                    throw new IllegalStateException("复制状态列缺席: " + name);
                values.put(name, value);
            });
            merge(values, overrides);
            values.putAll(target.target().predicates());
            values.put(target.version().column().name(), target.mapping().value(target.version().column().name(), 0L));
            copies.put(targetId, new Clone(versioned(source), target.target().table(), values));
        }
        /**
         * 集合缺席不足以证明允许删除；额外条件须在 DELETE 时再次核对，防止加载后状态已变化。
         *
         * @param id
         *            原始投影中存在、当前投影中已缺席的子行身份
         * @param protection
         *            非空的附加普通列条件，删除时再次核对；不得占用身份、版本或审计列
         */
        public void remove(K id, Map<String, Value> protection) {
            Row row = before.rows().get(id);
            if (row == null || after.rows().containsKey(id) || protection.isEmpty() || removals.containsKey(id)) {
                throw new IllegalStateException("只能显式移除已缺席的已加载子行，且必须附加保护条件");
            }
            permitted(row, protection);
            Map<String, Value> predicates = new LinkedHashMap<>(versioned(row).predicates());
            merge(predicates, protection);
            removals.put(id, new Delete(new Target(row.target().table(), predicates)));
        }
        /**
         * 核验贡献值属于声明列且未占用框架保留字段。
         *
         * @param row
         *            决定表结构和保留列范围的行投影
         * @param values
         *            待核验的普通字段、协议终值或删除保护条件
         */
        private void permitted(Row row, Map<String, Value> values) {
            row.mapping().check(values);
            values.keySet().forEach(name -> {
                if (row.mapping().identityColumns().contains(name) || row.mapping().versionColumn().equals(name)
                        || auditColumns.contains(name))
                    throw new IllegalStateException("不能覆盖身份、版本或审计列: " + name);
            });
        }
        /**
         * 在物理身份和原始版本不变的前提下比较普通字段终值。
         *
         * @param original
         *            保留原始定位及版本的旧行比较投影
         * @param current
         *            相同表、定位及原始版本的当前行投影
         * @return 普通字段变化收集器，不含审计和新锁版本
         */
        private JdbcRowChanges difference(Row original, Row current) {
            if (!original.target().equals(current.target()) || !original.mapping().equals(current.mapping())
                    || !original.version().equals(current.version()))
                throw new IllegalStateException("保存行身份或原始版本变化");
            permitted(current, current.values());
            permitted(original, original.values());
            return JdbcRowChanges.between(original.values(), current.values());
        }
        /**
         * 核验集合增删意图，附加审计和版本并按安全顺序封口更新计划。
         *
         * @param audit
         *            存在实际写入时提供审计值的延迟函数
         * @return 无变化计划，或需要先由 Store 执行根 CAS 的更新计划
         */
        public Plan finish(Supplier<Map<String, Value>> audit) {
            after.rows().keySet().forEach(id -> {
                if (!before.rows().containsKey(id) && !copies.containsKey(id))
                    throw new IllegalStateException("新增子行必须显式请求复制");
            });
            before.rows().keySet().forEach(id -> {
                if (!after.rows().containsKey(id) && !removals.containsKey(id))
                    throw new IllegalStateException("缺席子行不能隐式删除");
            });
            Supplier<Map<String, Value>> checkedAudit = () -> {
                Map<String, Value> values = immutable(audit.get());
                if (!values.keySet().equals(auditColumns))
                    throw new IllegalStateException("审计输出与声明不一致");
                return values;
            };
            List<Operation> operations = new ArrayList<>();
            // 先复制再更新来源，确保 Clone 的版本条件仍匹配；根快照最后写，根版本由 Store 先行 CAS。
            copies.forEach((id, copy) -> {
                Map<String, Value> values = JdbcRowChanges.of(copy.overrides()).complete(checkedAudit);
                after.rows().get(id).mapping().check(values);
                operations.add(new Clone(copy.source(), copy.target(), values));
            });
            updates.forEach((id, values) -> {
                contributions.getOrDefault(id, Map.of()).values().forEach(values::assign);
                Row row = before.rows().get(id);
                Map<String, Value> assignments = values.complete(checkedAudit, row.version());
                row.mapping().check(assignments);
                if (!assignments.isEmpty())
                    operations.add(new Change(versioned(row), assignments));
            });
            operations.addAll(removals.values());
            // 根版本留给 Store 的 CAS 递增，这里只封口字段和审计，避免重复递增版本。
            Map<String, Value> rootValues = root.complete(checkedAudit);
            before.root().mapping().check(rootValues);
            if (!rootValues.isEmpty())
                operations.add(new Change(before.root().target(), rootValues));
            return operations.isEmpty()
                    ? new NoChanges()
                    : new Update(before.root().target(), before.root().version(), operations);
        }
        /**
         * 将原始版本并入行定位条件，供复制来源、子行更新及删除保护使用。
         *
         * @param row
         *            包含物理身份与加载时原始版本的行投影
         * @return 同时匹配身份和原始版本的定位条件
         */
        private static Target versioned(Row row) {
            Map<String, Value> predicates = new LinkedHashMap<>(row.target().predicates());
            predicates.put(row.version().column().name(), row.version());
            return new Target(row.target().table(), predicates);
        }
        /**
         * 合并列终值，允许相同绑定值重复声明并拒绝同列冲突。
         *
         * @param target
         *            接收新增终值的可变映射
         * @param values
         *            待合并的列终值；同名已有值必须完全相等
         */
        private static void merge(Map<String, Value> target, Map<String, Value> values) {
            values.forEach((name, value) -> {
                Value previous = target.putIfAbsent(name, value);
                if (previous != null && !previous.equals(value))
                    throw new IllegalStateException("同列终值冲突: " + name);
            });
        }
    }
}
