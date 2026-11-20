package sk.tracker.infrastructure.repositories;

import sk.businessframework.infrastructure.repository.JdbcCustomFieldMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom columns of air_inst_tracker_rev.
 */
final class TrackerCustomFieldMapper implements JdbcCustomFieldMapper {
    private static final Map<String, Class<?>> COLUMNS = declareColumns();

    private static Map<String, Class<?>> declareColumns() {
        Map<String, Class<?>> columns = new LinkedHashMap<>();
        addSlots(columns, "date", 5, Long.class);
        addSlots(columns, "text", 10, String.class);
        addSlots(columns, "list", 10, Long.class);
        addSlots(columns, "multilist", 10, String.class);
        addSlots(columns, "numeric", 10, BigDecimal.class);
        addSlots(columns, "largetext", 5, String.class);
        addSlots(columns, "compositetext", 5, String.class);
        addSlots(columns, "compositenumber", 5, String.class);
        addSlots(columns, "startdate", 5, Long.class);
        addSlots(columns, "endstartdate", 5, Long.class);
        addSlots(columns, "richtext", 5, String.class);
        columns.put("reference1", Long.class);
        columns.put("reference01", Long.class);
        columns.put("reference001", Long.class);
        return Collections.unmodifiableMap(columns);
    }

    private static void addSlots(Map<String, Class<?>> columns, String prefix, int count, Class<?> type) {
        for (int index = 1; index <= count; index++) {
            String suffix = (index < 10 ? "0" : "") + index;
            columns.put(prefix + suffix, type);
            columns.put(prefix + "0" + suffix, type);
        }
    }

    @Override
    public Map<String, Class<?>> columns() {
        return COLUMNS;
    }
}
