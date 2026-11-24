package com.airedgesoft.lib.jdbc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;

/** 在无时区 SQL 时间戳和应用的 Unix 毫秒之间转换。 */
public final class JdbcLocalTimestamp {
    public static final Class<LocalDateTime> JDBC_VALUE_TYPE = LocalDateTime.class;

    private JdbcLocalTimestamp() {
    }

    /** 将 Unix 毫秒转为指定时区的本地时间供 JDBC 写入，保留空值。 */
    public static LocalDateTime toJdbcValue(Long epochMillis, ZoneId zone) {
        return epochMillis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone);
    }

    /** 按指定时区解释 JDBC 读出的本地时间，保留空值。 */
    public static Long toEpochMillis(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant().toEpochMilli();
    }

    /** 声明从 Unix 毫秒取值的无时区时间戳列。 */
    public static <S> JdbcRowMapping.Binding<S> column(String name, ZoneId zone, Function<S, Long> extractor) {
        return JdbcRowMapping.column(name, JDBC_VALUE_TYPE, source -> toJdbcValue(extractor.apply(source), zone));
    }
}
