package com.airedgesoft.sharedkernel.businessframework.domain.admin.object.model;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.CellSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.table.TableSnapshot;
import lombok.Data;

import java.util.List;

/**
 * 数据对象快照（替代 v3 的 {@code IInnerDataObject}/{@code IDataObject}）。
 *
 * <p>
 * 判权只需要「对象身份 + 分类 + 行/单元格 + 归属」，因此是扁平值；对象框架的读写、生命周期、历史等能力不进 V4。 方法名与 v3 原件一致，使迁入代码的调用点只换类型。
 */
@Data
public class ObjectSnapshot {

    /** 对象 ID */
    private Long id;

    /** 对象类型（分类 ID） */
    private Long type;

    /** 分类（子类/中类） */
    private ClassSnapshot airSubClass;

    /** 基础类 */
    private ClassSnapshot airBaseClass;

    /** 中类 */
    private ClassSnapshot airClass;

    /** 基础类 ID */
    private Long baseClassId;

    /** 公共表 ID */
    private Long commonId;

    /** 分支（tracker）ID */
    private Long trackerId;

    /** 工作区（产品空间）ID */
    private Long workSpace;

    /** 工作集 ID */
    private Long workingsetId;

    /** 版次记录的唯一 ID。 */
    private Long revisionId;

    /** 版次号（版次控制对象必有） */
    private Integer revision;

    /** 主行 */
    private RowSnapshot row;

    /** 无权限行 */
    private RowSnapshot noPrivilegeRow;

    /** 页签单元格集合 */
    private List<CellSnapshot> cells;

    /** 全部单元格集合 */
    private List<CellSnapshot> innerCells;

    /** 页签（表描述） */
    private TableSnapshot innerTable;

    /** 是否已删除 */
    private boolean deleted;

    /** v3 语义：中类（parent class） */
    public ClassSnapshot getAirClass() {
        return airClass;
    }

    /** 按属性 API ID 取单元格（v3 {@code getIInnerCell(String)}） */
    public CellSnapshot getIInnerCell(String apiId) {
        if (innerCells == null) {
            return null;
        }
        return innerCells.stream()
                .filter(cell -> cell.getAttribute() != null && apiId.equals(cell.getAttribute().getAPIID())).findFirst()
                .orElse(null);
    }

    /** v3 语义：取不带判权结论的原始行（{@code getNoPrivilegeRow}） */
    public RowSnapshot getNoPrivilegeRow() {
        return noPrivilegeRow;
    }

    /** v3 语义：取带判权结论的行（{@code getRow}） */
    public RowSnapshot getRow() {
        return row;
    }
}
