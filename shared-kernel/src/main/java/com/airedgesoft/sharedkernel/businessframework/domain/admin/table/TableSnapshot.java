package com.airedgesoft.sharedkernel.businessframework.domain.admin.table;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.AttributeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.object.model.RowSnapshot;
import lombok.Data;

import java.util.List;

/** 页签/表描述快照（替代 v3 的 {@code IInnerTableDesc}/{@code TableDesc}）。 */
@Data
public class TableSnapshot {

    /** 页签 ID */
    private Long id;

    /** 页签 API ID */
    private String apiId;

    /** 页签物理表名 */
    private String dbTableName;

    /** 页签属性列表 */
    private List<AttributeSnapshot> attributes;

    private List<RowSnapshot> rows;

    /** 是否版次控制（v3 TableDesc#getChangeControl：1=是） */
    private Integer changeControl;

    /** 是否启用（v3 Node#getEnabled） */
    private Long enabled;

    public String getAPIID() {
        return apiId;
    }
}
