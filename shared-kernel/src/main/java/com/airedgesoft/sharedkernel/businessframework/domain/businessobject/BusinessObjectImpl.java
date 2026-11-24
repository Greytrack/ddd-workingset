package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体：业务数据承载实现（«业务对象 类具体»）——{@link BusinessDataCarrier} 泛属性容器的独立承载实现。
 *
 * <p>
 * 只落状态与 SPI 访问器：{@code Map<String, FieldValue> fieldValues = new LinkedHashMap<>()}（写入保序、同 apiId 覆盖）； 容纳读写行为全部来自
 * {@link BusinessDataCarrier} 的 default 实现，本类不复写。
 *
 * <p>
 * 判等不做值判等（随宿主版次/聚合身份）。
 *
 * <p>
 * <b>Step 2 挂账</b>：本类当前无调用方，且它没有业务对象身份（id / 分类 / 生命周期）——是纯粹的「数据承载者」实现； 故 {@link BusinessObject} 身份成员一律返回
 * {@code null}，本类待裁决（保留或删除）。
 */
public class BusinessObjectImpl implements BusinessObject {

    /** BusinessData：字段集合（写入保序、同列名覆盖）。 */
    private final Map<String, FieldValue> fieldValues = new LinkedHashMap<>();

    @Override
    public Map<String, FieldValue> getBusinessData() {
        return fieldValues;
    }

    /**
     * 身份未落：占位实现，待本类裁决（第一步清单挂账）。
     *
     * @return 恒 {@code null}
     */
    @Override
    public Long getId() {
        return null;
    }

    /**
     * 分类未落：占位实现。
     *
     * @return 恒 {@code null}
     */
    @Override
    public Long getType() {
        return null;
    }

    /**
     * 空间归属未落：占位实现。
     *
     * @return 恒 {@code null}
     */
    @Override
    public Long getWorkSpace() {
        return null;
    }

    /**
     * 工作集归属未落：占位实现。
     *
     * @return 恒 {@code null}
     */
    @Override
    public Long getWorkingsetId() {
        return null;
    }

    /**
     * 共表身份 / 审计 / 结构字段与共享桶均未落：占位实现（本类待裁决，不入存储）。
     *
     * @return 恒 {@code null}（{@link #shared()} 除外，恒空 Map）
     */
    @Override
    public Long getCommonId() {
        return null;
    }

    @Override
    public Long getTrackerId() {
        return null;
    }

    @Override
    public String getNumber() {
        return null;
    }

    @Override
    public Long getParentId() {
        return null;
    }

    @Override
    public Long getCreatedBy() {
        return null;
    }

    @Override
    public Integer getDeleteFlag() {
        return null;
    }

    @Override
    public Map<String, FieldValue> shared() {
        return Map.of();
    }

}
