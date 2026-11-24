package com.airedgesoft.lib.restful.orderby;

/**
 * 排序方向（AIP-132 List 排序；缺省升序）。
 */
public enum SortDirection {

    ASC, DESC;

    /** SQL 片段。 */
    public String sql() {
        return name();
    }
}
