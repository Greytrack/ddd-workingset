package sk.businessframework.infrastructure.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the custom columns declared by a repository, preserving SQL NULL values.
 */
public interface JdbcCustomFieldMapper {
    Map<String, Class<?>> columns();

    default Map<String, Object> read(ResultSet rs) throws SQLException {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (var column : columns().entrySet()) {
            fields.put(column.getKey(), rs.getObject(column.getKey(), column.getValue()));
        }
        return Collections.unmodifiableMap(fields);
    }
}
