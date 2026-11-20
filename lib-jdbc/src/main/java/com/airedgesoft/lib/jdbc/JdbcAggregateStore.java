package com.airedgesoft.lib.jdbc;

import com.airedgesoft.lib.jdbc.AggregateMapping.TableMapping;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Column;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 串联加载、创建和更新生命周期，参与调用方已开启的写事务。 仓储在更新 hook 中重新读库并核对对象版本；本类不缓存原始行。
 *
 * <p>
 * 事务内只保留身份复用、保存状态；同一对象每事务只保存一次。 Store 执行计划中的根 CAS 和有序 SQL，回执不代表外层事务已经提交。
 *
 * <p>
 * <b>新增 / 更新的判定 = 本事务是否加载过该身份</b>：未加载 → INSERT（新建）；已加载 → UPDATE + 根 CAS。 因此<b>更新既有聚合必须先在同一事务内加载它</b>（否则按新建走 INSERT →
 * 主键冲突）；加载快照同时提供 CAS 基线版本。
 */
public final class JdbcAggregateStore {
    private static final java.util.Map<Class<?>, AggregateLifecycleListener> GLOBAL_LISTENERS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 注册全局聚合生命周期监听器（解耦基础设施与上层观测）。<b>按实现类幂等</b>：同类型重复注册（如 Spring 上下文刷新）以 {@code ConcurrentHashMap.put}
     * 原子替换既有实例——防累积重复触发，并发注册也不残留双份。 注册表为 JVM 级静态状态，前提是单宿主单上下文； 监听器随宿主生命周期经 {@link #unregisterListener} 注销。
     */
    public static void registerListener(AggregateLifecycleListener listener) {
        if (listener == null) {
            return;
        }
        GLOBAL_LISTENERS.put(listener.getClass(), listener);
    }

    /**
     * 注销全局聚合生命周期监听器。
     */
    public static void unregisterListener(AggregateLifecycleListener listener) {
        if (listener != null) {
            GLOBAL_LISTENERS.remove(listener.getClass(), listener);
        }
    }

    private final Object resourceKey = new Object();
    private final TransactionTemplate existingTransaction;
    private final JdbcWriteExecutor executor;
    private final NamedParameterJdbcTemplate reader;

    private static final class Entries<A, ID> {
        final Map<ID, A> identities = new HashMap<>();
        final Map<A, SaveState> objects = new IdentityHashMap<>();
        /**
         * 事务上下文持有的加载版本：key 存在 = 已有对象（即使值为 null，表示旧库 NULL 版本）； key 不存在 = 本事务未加载过，按新建处理。领域对象不持有该技术字段。
         */
        final Map<ID, Long> versions = new HashMap<>();

        /**
         * 事务结束后清除身份、保存状态与加载版本，避免跨事务复用。
         */
        void clear() {
            identities.clear();
            objects.clear();
            versions.clear();
        }
    }

    private enum SaveState {
        LOADED, SAVED
    }

    private static final class State {
        final Map<AggregateMapping<?, ?>, Entries<?, ?>> mappings = new IdentityHashMap<>();
        boolean invalid;
    }

    /**
     * 装配查询和执行组件，只参与调用方已开启的事务。
     *
     * @param transactionManager
     *            与 JDBC 数据源配套的事务管理器，用于强制参与已有事务并传播回滚状态
     * @param jdbc
     *            与外层事务共用数据源的 JdbcTemplate
     */
    public JdbcAggregateStore(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        executor = new JdbcWriteExecutor(Objects.requireNonNull(jdbc, "JDBC 执行器不能为空"));
        reader = new NamedParameterJdbcTemplate(jdbc);
        // MANDATORY 不创建独立事务；参与范围内写失败仍标记回滚，避免异常被外层捕获后提交部分写入。
        existingTransaction = new TransactionTemplate(transactionManager);
        existingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
    }

    /**
     * 仅支持已声明 BIGINT 列的集合过滤；表列标识符受控，业务值全部通过参数绑定。
     *
     * @param table
     *            声明读取表、列名和 JDBC 类型的元数据
     * @param filterColumn
     *            用于 IN 过滤的已声明 BIGINT 列名
     * @param ids
     *            非空引用且不含 null 的过滤值集合；空集合不执行查询
     * @return 仅含声明列的原始行列表，保留 SQL NULL；不保证查询顺序
     */
    public List<StoredRow> findAll(TableMapping table, String filterColumn, Collection<Long> ids) {
        // 当前只需要按根 ID/归属 ID 批量装配；限制为声明的 BIGINT 列，不开放任意条件 SQL。
        Column filter = table.columns().get(filterColumn);
        if (filter == null || filter.jdbcType() != Types.BIGINT) {
            throw new IllegalArgumentException("读取过滤列必须声明为 BIGINT: " + filterColumn);
        }
        // 发出 SQL 前检查全部列类型，避免等到某一行转换时才发现声明不受支持。
        table.columns().values().forEach(column -> javaType(column.jdbcType()));
        List<Long> keys = List.copyOf(ids);
        // 空批次不生成 IN ()，也不借空输入触发无范围查询。
        if (keys.isEmpty())
            return List.of();
        // 标识符来自已校验的表/列声明；仅查询已知列，未知扩展列不会进入快照值类型体系。
        String columns = table.columns().values().stream().map(Column::quoted).collect(Collectors.joining(", "));
        String sql = "SELECT " + columns + " FROM \"" + table.table().sqlName() + "\" WHERE " + filter.quoted()
                + " IN (:ids)";
        // 集合由 NamedParameterJdbcTemplate 展开并绑定，业务 ID 不参与 SQL 文本拼接。
        return reader.query(sql, Map.of("ids", keys), (result, index) -> read(result, table));
    }

    /**
     * 将结果集当前行读取为保留 SQL NULL 的不可变快照。
     *
     * @param result
     *            已定位到当前行的 JDBC 结果集
     * @param table
     *            决定读取列及类型的表声明
     * @return 当前行所有声明列的原始值快照
     */
    private static StoredRow read(ResultSet result, TableMapping table) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        // 即使值为 NULL 也登记列键，保证后续能够区分“已读取 NULL”和“列未加载”。
        for (Column column : table.columns().values()) {
            Object value = readValue(result, column);
            values.put(column.name(), value);
        }
        return new StoredRow(values);
    }

    /**
     * 按声明类型读取标量并恢复 SQL NULL，整数收窄时拒绝溢出。
     *
     * @param result
     *            已定位到当前行的 JDBC 结果集
     * @param column
     *            待读取的列名与 JDBC 类型
     * @return 声明类型对应的包装值或表示 SQL NULL 的 null
     */
    private static Object readValue(ResultSet result, Column column) throws SQLException {
        // 使用 JDBC 标量 getter 兼容旧库 int4/int8；getObject(name, Long.class) 在部分驱动上拒绝 int4。
        // 标量 getter 把 SQL NULL 返回为 0/false，必须紧接 wasNull 恢复 NULL，避免制造不存在的旧值。
        return switch (column.jdbcType()) {
            case Types.BIGINT -> {
                long number = result.getLong(column.name());
                yield result.wasNull() ? null : Long.valueOf(number);
            }
            case Types.INTEGER -> {
                long number = result.getLong(column.name());
                // 版次号/状态可声明为 Integer，但库中可能是 BIGINT；越界必须失败，不能静默截断。
                yield result.wasNull() ? null : Integer.valueOf(Math.toIntExact(number));
            }
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR -> result.getString(column.name());
            case Types.SMALLINT -> {
                short number = result.getShort(column.name());
                yield result.wasNull() ? null : Short.valueOf(number);
            }
            case Types.REAL -> {
                float number = result.getFloat(column.name());
                yield result.wasNull() ? null : Float.valueOf(number);
            }
            case Types.NUMERIC, Types.DECIMAL -> result.getBigDecimal(column.name());
            case Types.DATE -> result.getObject(column.name(), LocalDate.class);
            case Types.TIME -> result.getObject(column.name(), LocalTime.class);
            case Types.TIMESTAMP -> result.getObject(column.name(), LocalDateTime.class);
            case Types.TIMESTAMP_WITH_TIMEZONE -> result.getObject(column.name(), OffsetDateTime.class);
            case Types.OTHER -> result.getObject(column.name(), UUID.class);
            case Types.DOUBLE -> {
                double number = result.getDouble(column.name());
                yield result.wasNull() ? null : Double.valueOf(number);
            }
            case Types.BOOLEAN -> {
                boolean value = result.getBoolean(column.name());
                yield result.wasNull() ? null : Boolean.valueOf(value);
            }
            default -> throw new IllegalArgumentException("不支持的读取列类型: " + column.jdbcType());
        };
    }

    /**
     * 明确支持的标量范围，和存储行/写入值保持一致；遇到 jsonb、数组等类型时先设计其表示再接入。
     *
     * @param jdbcType
     *            声明的 java.sql.Types 类型值
     * @return 对应的 Java 包装类型；不支持的 JDBC 类型抛出 IllegalArgumentException
     */
    private static Class<?> javaType(int jdbcType) {
        return switch (jdbcType) {
            case Types.BIGINT -> Long.class;
            case Types.INTEGER -> Integer.class;
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR -> String.class;
            case Types.SMALLINT -> Short.class;
            case Types.REAL -> Float.class;
            case Types.NUMERIC, Types.DECIMAL -> BigDecimal.class;
            case Types.DATE -> LocalDate.class;
            case Types.TIME -> LocalTime.class;
            case Types.TIMESTAMP -> LocalDateTime.class;
            case Types.TIMESTAMP_WITH_TIMEZONE -> OffsetDateTime.class;
            case Types.OTHER -> UUID.class;
            case Types.DOUBLE -> Double.class;
            case Types.BOOLEAN -> Boolean.class;
            default -> throw new IllegalArgumentException("不支持的读取列类型: " + jdbcType);
        };
    }

    /** 仅表示当前事务内的执行结果；提交由外层决定，调用方不能据此认定持久化已完成。 */
    public sealed interface Receipt {
        record Unchanged() implements Receipt {
        }
        enum Kind {
            CREATED, UPDATED
        }
        record Applied(Kind kind, long rootVersion, int affectedRows) implements Receipt {
            /**
             * 校验保存回执具备操作种类、有效根版本和至少一行写入结果。
             *
             * @param kind
             *            非空的新增或更新标记
             * @param rootVersion
             *            执行后的非负根锁版本
             * @param affectedRows
             *            计划执行涉及的行操作计数；更新时包含一次根 CAS，至少为 1
             */
            public Applied {
                if (kind == null || rootVersion < 0 || affectedRows < 1)
                    throw new IllegalArgumentException("保存回执不完整");
            }
        }
    }

    /**
     * 按 ID 与可空锁版本选择创建或更新 hook；已有对象由仓储重新读库并核对版本。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param aggregate
     *            待保存对象；新建/更新由本事务是否加载过该身份判定，领域对象不携带版本
     * @return 当前事务内的执行回执；包含无变化结果，不代表外层事务已提交
     */
    public <A, ID> Receipt save(AggregateMapping<A, ID> mapping, A aggregate) {
        ID id = mapping.identity(aggregate);
        Receipt receipt = save(mapping, aggregate,
                (current, isNew) -> isNew
                        ? execute(mapping.createPlan(current))
                        : execute(mapping.updatePlan(current, loadedVersion(mapping, id))));
        // 保存成功后回写本事务的加载版本：Create 为 0；Update 为 CAS 递增后的版本；Unchanged 保持加载值。
        if (receipt instanceof Receipt.Applied applied) {
            State state = state(false);
            if (state != null) {
                entries(state, mapping).versions.put(id, applied.rootVersion());
            }
            for (AggregateLifecycleListener listener : GLOBAL_LISTENERS.values()) {
                listener.onSaved(id, aggregate, applied.rootVersion());
            }
        }
        return receipt;
    }

    /** 当前事务上下文记录的加载版本（未加载 → null）。 */
    private <A, ID> Long loadedVersion(AggregateMapping<A, ID> mapping, ID id) {
        State state = state(false);
        return state == null ? null : entries(state, mapping).versions.get(id);
    }

    /**
     * 执行新增、无变化或先根 CAS 后子行操作的更新计划。
     *
     * @param plan
     *            映射编译后的不可变聚合写入计划
     * @return 事务内执行回执；根 CAS 冲突时中止后续行操作
     */
    private Receipt execute(AggregateMapping.Plan plan) {
        if (plan instanceof AggregateMapping.NoChanges)
            return new Receipt.Unchanged();
        if (plan instanceof AggregateMapping.Create create) {
            executor.execute(new JdbcWritePlan(create.operations()));
            return new Receipt.Applied(Receipt.Kind.CREATED, create.rootVersion(), create.operations().size());
        }
        AggregateMapping.Update update = (AggregateMapping.Update) plan;
        // 先竞争根版本，冲突时不写子行；该锁只协调同样遵守根版本协议的写入方。
        Long version = executor.compareAndSwap(update.root(), update.expectedVersion().column(),
                update.expectedVersion());
        executor.execute(new JdbcWritePlan(update.operations()));
        return new Receipt.Applied(Receipt.Kind.UPDATED, version, update.operations().size() + 1);
    }

    /**
     * 写事务中复用同一身份的对象，避免两份可变副本相互覆盖；只缓存领域对象以复用身份，不缓存原始行；后续保存由更新 hook 重新读库。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param id
     *            请求的聚合身份；null 直接返回空结果
     * @param loader
     *            缓存未命中时按请求身份加载原始记录的函数
     * @return 匹配身份的聚合；身份为空或原始记录不存在时为空
     */
    public <A, ID> Optional<A> findById(AggregateMapping<A, ID> mapping, ID id,
            Function<ID, Optional<AggregateRows>> loader) {
        if (id == null) {
            return Optional.empty();
        }
        Optional<A> cached = cached(mapping, id);
        if (cached.isPresent()) {
            return cached;
        }
        try {
            return loader.apply(id).map(row -> {
                if (!Objects.equals(mapping.storedIdentity(row), id)) {
                    throw new IllegalStateException("加载记录与请求身份不一致");
                }
                return reconstruct(mapping, row);
            });
        } catch (RuntimeException | Error failure) {
            invalidate();
            throw failure;
        }
    }

    /**
     * 写事务内同一身份复用同一对象，避免两份可变副本各自修改后相互覆盖。结果保留输入顺序和重复项。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param ids
     *            请求身份集合；null 或空集合返回空列表，null 身份被忽略
     * @param loader
     *            批量加载尚未缓存的去重身份；不得返回未请求或重复身份
     * @return 按输入顺序排列的已找到聚合列表，忽略未找到的身份并保留重复项
     */
    public <A, ID> List<A> findByIds(AggregateMapping<A, ID> mapping, Collection<ID> ids,
            Function<Collection<ID>, ? extends Iterable<AggregateRows>> loader) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<ID, A> found = new HashMap<>();
        Set<ID> missing = new LinkedHashSet<>();
        for (ID id : ids) {
            if (id == null) {
                continue;
            }
            Optional<A> cached = cached(mapping, id);
            if (cached.isPresent()) {
                found.put(id, cached.get());
            } else {
                missing.add(id);
            }
        }
        try {
            if (!missing.isEmpty()) {
                for (AggregateRows row : loader.apply(List.copyOf(missing))) {
                    ID id = mapping.storedIdentity(row);
                    if (!missing.contains(id) || found.containsKey(id)) {
                        throw new IllegalStateException("批量加载返回了未请求或重复的身份");
                    }
                    found.put(id, reconstruct(mapping, row));
                }
            }
            List<A> result = new ArrayList<>();
            for (ID id : ids) {
                if (id != null && found.containsKey(id)) {
                    result.add(found.get(id));
                }
            }
            return result;
        } catch (RuntimeException | Error failure) {
            invalidate();
            throw failure;
        }
    }

    /**
     * 调用仓储加载 hook 并缓存领域对象；不保留传入的原始行。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param stored
     *            尚未施加领域默认值的原始加载记录
     * @return 身份与原始记录一致的重建聚合
     */
    private <A, ID> A reconstruct(AggregateMapping<A, ID> mapping, AggregateRows stored) {
        A aggregate = mapping.reconstruct(stored);
        loaded(mapping, aggregate);
        // 原始行快照即加载版本来源：记录后，同一事务内的保存按更新处理并参与 CAS 前核验。
        Long version = mapping.storedVersion(stored);
        ID id = mapping.identity(aggregate);
        State state = state(false);
        if (state != null) {
            // 基线按「首次登记」保守方向：身份缓存（claim）保证同 id 单实例，id 槽即实例槽；
            // putIfAbsent 防御性保证即便绕过缓存二次装载也不会以较新版本覆盖既有基线（§6.7 规则 5）
            entries(state, mapping).versions.putIfAbsent(id, version);
        }
        for (AggregateLifecycleListener listener : GLOBAL_LISTENERS.values()) {
            listener.onLoaded(id, aggregate, version);
        }
        return aggregate;
    }

    /**
     * 查询当前写事务的身份缓存，失效上下文会拒绝访问。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param id
     *            需要复用的聚合身份
     * @return 缓存中的同一聚合对象；无写事务或未命中时为空
     */
    <A, ID> Optional<A> cached(AggregateMapping<A, ID> mapping, ID id) {
        State state = state(false);
        return state == null ? Optional.empty() : Optional.ofNullable(entries(state, mapping).identities.get(id));
    }

    /**
     * 在写事务中按身份复用领域对象；只读和无事务读取不建立身份缓存。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param aggregate
     *            待保存对象；新建/更新由本事务是否加载过该身份判定，领域对象不携带版本
     */
    <A, ID> void loaded(AggregateMapping<A, ID> mapping, A aggregate) {
        State state = state(false);
        if (state != null) {
            Entries<A, ID> entries = entries(state, mapping);
            ID id = mapping.identity(aggregate);
            claim(entries, id, aggregate);
            entries.objects.put(aggregate, SaveState.LOADED);
            // 已登记身份的旧对象即使调用方未提供原始行，也视为已加载（版本未知时以 null 参与 CAS）。
            entries.versions.putIfAbsent(id, null);
        }
    }

    /**
     * 只参与外部写事务；回调返回不代表提交。任何写入失败均使所有映射失效，即使外层捕获异常也必须回滚。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param <R>
     *            写入回调结果类型
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @param aggregate
     *            待保存对象；新建/更新由本事务是否加载过该身份判定，领域对象不携带版本
     * @param writer
     *            接收聚合及是否按 ID/锁版本判定为新对象的回调；已有对象的数据由回调自行查询
     * @return 写入回调结果；回调返回不代表事务已提交
     */
    <A, ID, R> R save(AggregateMapping<A, ID> mapping, A aggregate, BiFunction<A, Boolean, R> writer) {
        Objects.requireNonNull(aggregate, "保存对象不能为空");
        Objects.requireNonNull(writer, "写入回调不能为空");
        return existingTransaction.execute(status -> {
            try {
                Entries<A, ID> entries = entries(state(true), mapping);
                boolean isNew = prepare(mapping, aggregate, entries);
                return writer.apply(aggregate, isNew);
            } catch (RuntimeException | Error failure) {
                invalidate();
                throw failure;
            }
        });
    }

    /**
     * 内部失败处理：使当前事务状态失效，不提供对外恢复或手动登记入口。
     */
    void invalidate() {
        State state = (State) TransactionSynchronizationManager.getResource(resourceKey);
        if (state != null) {
            state.invalid = true;
        }
    }

    /**
     * 核验事务内身份并占用一次保存机会，再按 ID/锁版本选择创建或更新路径。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param mapping
     *            提取领域身份的映射实例
     * @param aggregate
     *            待保存的对象引用
     * @param entries
     *            当前事务的身份与保存状态，不包含数据库原始行
     * @return true 表示按 ID/锁版本判定的新对象，false 表示需要由仓储重新读库的已有对象
     */
    private static <A, ID> boolean prepare(AggregateMapping<A, ID> mapping, A aggregate, Entries<A, ID> entries) {
        SaveState previous = entries.objects.get(aggregate);
        if (previous == SaveState.SAVED)
            throw new IllegalStateException("同一事务不能重复保存聚合");
        ID id = mapping.identity(aggregate);
        if (previous == null) {
            claim(entries, id, aggregate);
        } else if (entries.identities.get(id) != aggregate) {
            throw new IllegalStateException("聚合身份不能变化");
        }
        // 回调之前登记，阻止递归保存；不缓存回调查询到的数据库行。
        entries.objects.put(aggregate, SaveState.SAVED);
        // 新建 = 本事务未加载过该身份；已有对象必须先加载（仓储保存前重读快照的既有约束）。
        return id == null || !entries.versions.containsKey(id);
    }

    /**
     * 独占登记聚合身份，阻止同一事务出现多个可写对象。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param entries
     *            当前事务内该映射的身份记录
     * @param id
     *            对象占用的稳定聚合身份
     * @param aggregate
     *            占用该身份的对象引用；身份已登记时拒绝重复登记
     */
    private static <A, ID> void claim(Entries<A, ID> entries, ID id, A aggregate) {
        if (entries.identities.putIfAbsent(id, aggregate) != null) {
            throw new IllegalStateException("一个事务内同一映射同一身份只能有一个写对象");
        }
    }

    /**
     * 按映射实例隔离事务记录，将类型擦除限制在统一入口。
     *
     * @param <A>
     *            领域聚合类型
     * @param <ID>
     *            聚合身份类型
     * @param state
     *            当前事务的持久化上下文
     * @param mapping
     *            查询、重建与保存共用的映射实例，用于隔离事务内身份记录
     * @return 当前映射专属的事务记录，不存在时创建
     */
    @SuppressWarnings("unchecked")
    private static <A, ID> Entries<A, ID> entries(State state, AggregateMapping<A, ID> mapping) {
        // 每个不可变 AggregateMapping 实例只创建同一泛型组合的 Entries，类型擦除仅封装在此处。
        return (Entries<A, ID>) state.mappings.computeIfAbsent(mapping, ignored -> new Entries<>());
    }

    /**
     * 获取或初始化当前写事务上下文，并拒绝使用已失效状态。
     *
     * @param required
     *            是否强制要求外部写事务；true 时缺少事务抛异常，false 时返回 null
     * @return 有效写事务上下文；非强制且无可用写事务时为 null
     */
    private State state(boolean required) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            if (required) {
                throw new IllegalStateException("聚合写入必须处于外部写事务");
            }
            return null;
        }
        State state = (State) TransactionSynchronizationManager.getResource(resourceKey);
        if (state == null) {
            state = new State();
            TransactionSynchronizationManager.bindResource(resourceKey, state);
            registerCleanup(state);
        }
        if (state.invalid) {
            throw new IllegalStateException("持久化上下文已失效，必须回滚并重新加载");
        }
        return state;
    }

    /**
     * 注册事务挂起、恢复和结束回调，隔离嵌套事务并清理保存资格。
     *
     * @param state
     *            与当前事务绑定、需要在挂起和结束时管理的上下文
     */
    private void registerCleanup(State state) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务挂起时解绑上下文，防止 REQUIRES_NEW 借用外层保存资格。
             */
            @Override
            public void suspend() {
                // REQUIRES_NEW 必须拥有独立加载记录；内层不能借用外层身份缓存。
                TransactionSynchronizationManager.unbindResource(resourceKey);
            }
            /**
             * 外层事务恢复时重新绑定其原有持久化上下文。
             */
            @Override
            public void resume() {
                TransactionSynchronizationManager.bindResource(resourceKey, state);
            }
            /**
             * 事务提交或回滚后解绑并清除所有映射的身份及保存记录。
             *
             * @param status
             *            Spring 事务完成状态；提交和回滚均执行同样的清理
             */
            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.getResource(resourceKey) == state) {
                    TransactionSynchronizationManager.unbindResource(resourceKey);
                }
                state.mappings.values().forEach(Entries::clear);
                state.mappings.clear();
            }
        });
    }
}
