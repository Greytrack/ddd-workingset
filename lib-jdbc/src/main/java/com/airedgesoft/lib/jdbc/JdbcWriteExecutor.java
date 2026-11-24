package com.airedgesoft.lib.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.airedgesoft.lib.jdbc.JdbcWritePlan.*;

/**
 * 将 PostgreSQL 方言与参数绑定集中在执行边界，避免领域映射混入 SQL 拼装和驱动细节。 本类不建立事务；多步写入的原子性依赖调用方提供的事务。 保持包级可见，让写入经 Store 协调加载资格、根 CAS
 * 和失败回滚，而非由业务仓储直接逐条执行。
 *
 * <p>
 * 日志仅记录操作、表名、行数和耗时，使写入失败可定位，同时避免输出业务值与定位参数。
 */
@Slf4j
final class JdbcWriteExecutor {

    // 操作都以单行定位；行数不符不能当作成功，否则可能提交部分聚合状态。
    private static final int EXACTLY_ONE_ROW = 1;
    private static final int NO_MATCHING_ROW = 0;

    private static final long NANOS_PER_MILLIS = 1_000_000L;

    // formatted 只填受控标识符和内部生成的 SQL 片段；业务值仍通过 ? 绑定。
    private static final String CAS_SQL = """
            UPDATE %1$s
            SET %2$s = COALESCE(%2$s, 0) + 1
            WHERE %3$s
              AND %2$s IS NOT DISTINCT FROM ?
            RETURNING %2$s
            """;
    private static final String INSERT_SQL = """
            INSERT INTO %s (%s)
            VALUES (%s)
            """;
    private static final String UPDATE_SQL = """
            UPDATE %s
            SET %s
            WHERE %s
            """;
    private static final String DELETE_SQL = """
            DELETE FROM %s
            WHERE %s
            """;
    private static final String CLONE_SQL = """
            INSERT INTO %s (%s)
            SELECT %s
            FROM %s
            WHERE %s
            """;
    private static final String COLUMN_METADATA_SQL = """
            SELECT *
            FROM %s
            WHERE 1 = 0
            """;
    private static final String DATABASE_MANAGED_COLUMNS_SQL = """
            SELECT attname,
                   (attgenerated <> '' OR attidentity <> '') AS managed
            FROM pg_catalog.pg_attribute
            WHERE attrelid = pg_catalog.to_regclass(?)
              AND attnum > 0
              AND NOT attisdropped
            """;

    private final JdbcTemplate jdbc;

    /**
     * 使用调用方的 JDBC 连接与事务执行写入，不自行建立事务。
     *
     * @param jdbc
     *            执行 SQL 的 JdbcTemplate，应与外层事务使用同一数据源
     */
    JdbcWriteExecutor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 依次执行已编译操作并记录总耗时；空计划直接跳过。
     *
     * @param plan
     *            已冻结顺序及绑定值的行操作计划
     */
    void execute(JdbcWritePlan plan) {
        if (plan.operations().isEmpty()) {
            return;
        }
        long startedAt = System.nanoTime();
        plan.operations().forEach(this::execute);
        log.debug("JDBC 计划执行完成：操作数={} 总耗时={}ms", plan.operations().size(),
                (System.nanoTime() - startedAt) / NANOS_PER_MILLIS);
    }

    /**
     * 用 null-safe 比较兼容旧 NULL 版本；递增与返回在同一 SQL 中完成，避免另查版本引入竞争窗口。
     *
     * @param target
     *            根行身份定位条件
     * @param version
     *            需要递增的锁版本列
     * @param expected
     *            加载时的原始版本绑定值，列必须与 version 一致；支持 SQL NULL
     * @return 数据库返回的递增后版本；未匹配时抛出乐观锁异常，匹配多行时抛出行数异常
     */
    Long compareAndSwap(Target target, Column version, Value expected) {
        if (!version.equals(expected.column())) {
            throw new IllegalArgumentException("版本列与预期值类型不一致");
        }
        List<Value> parameters = new ArrayList<>(target.predicates().values());
        parameters.add(expected);
        String sql = CAS_SQL.formatted(target.table().sqlName(), version.quoted(), predicate(target));
        long startedAt = System.nanoTime();
        List<Long> versions = jdbc.query(sql, ps -> bind(ps, parameters), (rs, row) -> rs.getLong(1));
        log.debug("执行 [CAS {}] 预期={} 实际={} 耗时={}ms", target.table().sqlName(), EXACTLY_ONE_ROW, versions.size(),
                (System.nanoTime() - startedAt) / NANOS_PER_MILLIS);
        if (versions.size() == NO_MATCHING_ROW) {
            // 并发版本冲突：技术异常不出栈，转为统一领域异常（409 VERSION_CONFLICT），并带上期望版本
            log.warn("[CAS {}] 未匹配预期状态（预期={} 实际={}）", target.table().sqlName(), EXACTLY_ONE_ROW, versions.size());
            throw new com.airedgesoft.lib.exception.DomainException(
                    com.airedgesoft.lib.exception.CommonResCode.VERSION_CONFLICT, target.table().sqlName(),
                    expected.value());
        }
        requireOne(versions.size(), "CAS " + target.table().sqlName(), true);
        return versions.get(0);
    }

    /**
     * 按操作类型分派 SQL 执行，并要求恰好影响一行。
     *
     * @param operation
     *            待执行的插入、更新、复制或删除操作
     */
    private void execute(Operation operation) {
        if (operation instanceof Insert insert) {
            insert(insert);
        } else if (operation instanceof Change change) {
            update(change);
        } else if (operation instanceof Clone clone) {
            cloneRow(clone);
        } else if (operation instanceof Delete delete) {
            executeOne(DELETE_SQL.formatted(delete.target().table().sqlName(), predicate(delete.target())),
                    new ArrayList<>(delete.target().predicates().values()),
                    "DELETE " + delete.target().table().sqlName(), true);
        }
    }

    /**
     * 将显式列赋值编译为单行 INSERT 并绑定参数。
     *
     * @param insert
     *            目标表和非空插入赋值
     */
    private void insert(Insert insert) {
        List<Value> values = new ArrayList<>(insert.values().values());
        String columns = values.stream().map(v -> v.column().quoted()).collect(Collectors.joining(", "));
        String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(", "));
        executeOne(INSERT_SQL.formatted(insert.table().sqlName(), columns, placeholders), values,
                "INSERT " + insert.table().sqlName(), false);
    }

    /**
     * 将字段终值与独立定位条件编译为单行 UPDATE。
     *
     * @param change
     *            待更新终值与行定位条件，条件可包含原始版本
     */
    private void update(Change change) {
        List<Value> parameters = new ArrayList<>(change.values().values());
        String assignments = parameters.stream().map(v -> v.column().quoted() + " = ?")
                .collect(Collectors.joining(", "));
        parameters.addAll(change.target().predicates().values());
        executeOne(UPDATE_SQL.formatted(change.target().table().sqlName(), assignments, predicate(change.target())),
                parameters, "UPDATE " + change.target().table().sqlName(), true);
    }

    /**
     * 通过 INSERT SELECT 在数据库内复制行，保留未覆盖的普通列。
     *
     * @param clone
     *            来源定位、目标表和覆盖值，身份及初始版本由计划提供
     */
    private void cloneRow(Clone clone) {
        List<Column> columns = cloneColumns(clone);
        Map<String, Value> overrides = clone.overrides();
        List<Value> parameters = new ArrayList<>();
        List<String> expressions = new ArrayList<>();
        for (Column column : columns) {
            Value override = overrides.get(column.name());
            expressions.add(override == null ? column.quoted() : "?");
            if (override != null) {
                parameters.add(override);
            }
        }
        parameters.addAll(clone.source().predicates().values());
        String names = columns.stream().map(Column::quoted).collect(Collectors.joining(", "));
        executeOne(
                CLONE_SQL.formatted(clone.target().sqlName(), names, String.join(", ", expressions),
                        clone.source().table().sqlName(), predicate(clone.source())),
                parameters, "CLONE " + clone.target().sqlName(), true);
    }

    /**
     * 确定可复制列，拒绝无效覆盖并排除数据库生成列。
     *
     * @param clone
     *            需要核对来源和目标表列结构的复制操作
     * @return 按目标表列顺序排列的普通列，未覆盖列必须在来源表存在
     */
    private List<Column> cloneColumns(Clone clone) {
        List<Column> targetColumns = columns(clone.target());
        Map<String, Boolean> managed = databaseManagedColumns(clone.target());
        Map<String, Value> overrides = clone.overrides();
        for (String name : overrides.keySet()) {
            if (targetColumns.stream().noneMatch(column -> column.name().equals(name))) {
                throw new InvalidDataAccessApiUsageException("Clone 覆盖列不存在：" + clone.target().sqlName() + "." + name);
            }
            if (Boolean.TRUE.equals(managed.get(name))) {
                throw new InvalidDataAccessApiUsageException("Clone 不允许覆盖数据库生成列：" + name);
            }
        }
        // generated/identity 由数据库重新生成；其余未覆盖列在数据库内复制，保留未知业务内容。
        List<Column> columns = targetColumns.stream().filter(column -> !Boolean.TRUE.equals(managed.get(column.name())))
                .toList();
        if (columns.isEmpty()) {
            throw new InvalidDataAccessApiUsageException("Clone 没有可复制的普通列");
        }
        List<Column> sourceColumns = clone.source().table().equals(clone.target())
                ? targetColumns
                : columns(clone.source().table());
        for (Column column : columns) {
            if (!overrides.containsKey(column.name())
                    && sourceColumns.stream().noneMatch(source -> source.name().equals(column.name()))) {
                throw new InvalidDataAccessApiUsageException("Clone 来源缺少列：" + column.name());
            }
        }
        return columns;
    }

    /**
     * 使用空结果查询读取真实表结构，供复制时保留未知普通列。
     *
     * @param table
     *            需要查询列元数据的受控表
     * @return 按数据库元数据顺序排列的列名及 JDBC 类型
     */
    private List<Column> columns(Table table) {
        return jdbc.query(COLUMN_METADATA_SQL.formatted(table.sqlName()), rs -> {
            List<Column> result = new ArrayList<>();
            for (int index = 1; index <= rs.getMetaData().getColumnCount(); index++) {
                result.add(new Column(rs.getMetaData().getColumnName(index), rs.getMetaData().getColumnType(index)));
            }
            return result;
        });
    }

    /**
     * 按当前连接的 search_path 识别数据库管理的生成列和 identity 列。
     *
     * @param table
     *            需要查询 PostgreSQL 系统目录的目标表
     * @return 列名到是否由数据库生成的标记映射
     */
    private Map<String, Boolean> databaseManagedColumns(Table table) {
        // to_regclass 使用当前连接的 search_path，避免误读 public 或另一个测试 schema。
        return jdbc.query(DATABASE_MANAGED_COLUMNS_SQL, rs -> {
            Map<String, Boolean> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString("attname"), rs.getBoolean("managed"));
            }
            return result;
        }, table.sqlName());
    }

    /**
     * 执行参数化 DML 并检查影响行数，避免部分聚合写入被当作成功。
     *
     * @param sql
     *            已拼装受控标识符、业务值使用占位符的 SQL
     * @param parameters
     *            按占位符顺序排列的带类型绑定值
     * @param operation
     *            仅用于日志和异常的操作名称及表名，不含业务值
     * @param guarded
     *            是否将零行视为预期状态失效；true 抛乐观锁异常，false 抛行数异常
     */
    private void executeOne(String sql, List<Value> parameters, String operation, boolean guarded) {
        long startedAt = System.nanoTime();
        int affected = jdbc.update(sql, ps -> bind(ps, parameters));
        log.debug("执行 [{}] 预期={} 实际={} 耗时={}ms", operation, EXACTLY_ONE_ROW, affected,
                (System.nanoTime() - startedAt) / NANOS_PER_MILLIS);
        requireOne(affected, operation, guarded);
    }

    /**
     * 要求恰好匹配一行，区分预期状态失效与定位范围错误。
     *
     * @param actual
     *            SQL 实际匹配或影响的行数
     * @param operation
     *            用于异常和日志的操作说明，不含参数值
     * @param guarded
     *            true 时将零行解释为乐观锁失败；其他非一行结果均为行数异常
     */
    private static void requireOne(int actual, String operation, boolean guarded) {
        if (actual == NO_MATCHING_ROW && guarded) {
            log.warn("[{}] 未匹配预期状态（影响行数={}，预期状态或来源已失效）", operation, NO_MATCHING_ROW);
            // 并发版本冲突：技术异常不出栈，转为统一领域异常（409 VERSION_CONFLICT），业务侧无需捕获
            throw new com.airedgesoft.lib.exception.DomainException(
                    com.airedgesoft.lib.exception.CommonResCode.VERSION_CONFLICT, operation, "未知");
        }
        if (actual != EXACTLY_ONE_ROW) {
            // 多行是定位/结构错误，不向业务层误报为可重试的版本冲突；消息不包含参数值。
            log.warn("[{}] 影响行数异常（预期={} 实际={}）", operation, EXACTLY_ONE_ROW, actual);
            throw new IncorrectResultSizeDataAccessException(operation + " 影响行数异常", EXACTLY_ONE_ROW, actual);
        }
    }

    /**
     * 生成 null-safe 等值定位条件，保留原始 SQL NULL 的比较含义。
     *
     * @param target
     *            包含非空有序条件映射的行定位
     * @return 以 AND 连接的带占位符条件片段；参数顺序与条件映射一致
     */
    private static String predicate(Target target) {
        return target.predicates().values().stream().map(v -> v.column().quoted() + " IS NOT DISTINCT FROM ?")
                .collect(Collectors.joining(" AND "));
    }

    /**
     * 按占位符顺序绑定标量值，并为 SQL NULL 指定 JDBC 类型。
     *
     * @param statement
     *            待设置参数的 JDBC 预编译语句
     * @param values
     *            按 SQL 占位符顺序排列的列值，JDBC 下标从 1 开始
     */
    private static void bind(PreparedStatement statement, List<Value> values) throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            Value value = values.get(index);
            if (value.value() == null) {
                statement.setNull(index + 1, value.column().jdbcType());
            } else {
                statement.setObject(index + 1, value.value(), value.column().jdbcType());
            }
        }
    }

}
