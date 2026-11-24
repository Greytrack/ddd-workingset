package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId;

import java.util.List;

/**
 * 值对象：差异集（一条关系行在一次比对中的字段差异 + 参与比对的属性总数）。
 *
 * @param relationRowId
 *            关系行标识
 * @param totalAttributes
 *            参与比对的属性总数（两端列并集）
 * @param changes
 *            字段差异项（空集＝两端无差异）
 */
public record DifferenceSet(RelationRowId relationRowId, int totalAttributes, List<FieldChange> changes) {

    public DifferenceSet {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /** 是否无差异。 */
    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
