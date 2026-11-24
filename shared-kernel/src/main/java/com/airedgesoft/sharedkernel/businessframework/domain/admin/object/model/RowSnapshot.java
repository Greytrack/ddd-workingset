package com.airedgesoft.sharedkernel.businessframework.domain.admin.object.model;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.CellSnapshot;
import lombok.Data;

import java.util.List;

/** 行快照（替代 v3 {@code Row}/{@code IRow}）：承载单元格集合与关联信息，判权结论写回单元格与 {@code discovered}。 */
@Data
public class RowSnapshot {

    private Long id;

    private Long associationTableId;

    private Long associationRowId;

    private Long relatedType;

    private Long relatedInstanceId;

    private Integer revision;

    private List<CellSnapshot> cells;

    private List<CellSnapshot> cellList;

    private boolean discovered;

    private List<CellSnapshot> cellDataList;

    private List<RowSnapshot> children;

    private Long instanceId;

    private Long instanceType;

    private Object parent;

    private Long workingsetId;

    private Long workSpace;

    private Long commonId;

    private Long trackerId;

    /** v3 语义：行可见性标记（Discovery 判权写回） */
    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }
    /** 空行：用于「无权限行」缺失时的占位，避免判权链路 NPE */
    public static RowSnapshot empty() {
        RowSnapshot row = new RowSnapshot();
        row.setCells(List.of());
        row.setCellList(List.of());
        row.setCellDataList(List.of());
        return row;
    }
}
