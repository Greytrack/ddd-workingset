package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;
import java.util.Map;

/**
 * 同对象属性值状态（对应 IDDD-ch5.3 改造定稿 {@code AttributeObjectState <<record>>}）。
 *
 * <p>
 * 升格自旧栈 {@code CheckedContext.objectValueByApiId}（apiId → 值，含本次未变更字段）， 供跨字段校验使用：如级联列表属性的从值必须落在「主属性当前值的允许从值集」内。
 *
 * <p>
 * <b>null 语义照旧</b>：值为 {@code null}（或未提供本对象）表示调用方未提供上下文， 跨字段校验按「无法判定」放行——与旧栈口径一致；非 null 但缺 key 则照旧参与判定。
 *
 * @param objectValueByApiId
 *            同对象属性值（apiId → 值）；可为 null（无法判定放行）
 */
public record AttributeObjectState(Map<String, Object> objectValueByApiId) {

    /** 由属性值映射构建；null 照旧表达「无法判定」。 */
    public static AttributeObjectState of(Map<String, Object> objectValueByApiId) {
        return new AttributeObjectState(objectValueByApiId);
    }

    /** 未提供上下文（null）时返回 null——调用方按「无法判定放行」处理。 */
    public Object valueOf(String apiId) {
        return objectValueByApiId == null ? null : objectValueByApiId.get(apiId);
    }
}
