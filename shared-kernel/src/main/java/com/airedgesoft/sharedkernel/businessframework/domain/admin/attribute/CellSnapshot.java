package com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute;

import lombok.Data;

/** 单元格快照（替代 v3 {@code Cell}/{@code IInnerCell}）：判权把结论写回 {@code canView}/{@code canModify}。 */
@Data
public class CellSnapshot {

    private AttributeSnapshot attribute;

    private boolean canView;

    private boolean canModify;

    private Object innerValue;

    public boolean hasReadPrivilege() {
        return canView;
    }

    public boolean hasModifyPrivilege() {
        return canModify;
    }
}
