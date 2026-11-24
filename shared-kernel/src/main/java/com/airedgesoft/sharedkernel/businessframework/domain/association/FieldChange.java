package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;

/**
 * 值对象：单个字段改动（差异集的最小单元）。
 *
 * @param column
 *            落库列名（业务数据键）
 * @param before
 *            改动前值（源侧；缺失为 null）
 * @param after
 *            改动后值（目标侧；缺失为 null）
 */
public record FieldChange(String column, FieldValue before, FieldValue after) {
}
