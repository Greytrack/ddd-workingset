package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.EnabledListEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeValueMetadataPort.CascadeRelationRow;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeValueMetadataPort.ListItemView;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeValueMetadataPort.MainAttributeView;

import java.util.*;

/**
 * 级联列表属性「从值范围」校验（原旧栈 {@code CascadeValueRangeChecker} 整体平移）。
 *
 * <p>
 * 规则（与业务端 cascade_relations/options 口径一致）：
 * <ol>
 * <li>主属性当前值为空 → 从值必须为空；</li>
 * <li>主值有值但未登记级联关系、或该主值下无「启用」的从值 → 从值必须为空；</li>
 * <li>否则从值必须属于该主值下已登记且启用的从值集合（级联单选=单值、级联多选=逗号逐项）；</li>
 * <li>主属性被禁用不影响本校验（实例上的值仍可读取）；</li>
 * <li>调用方未提供对象值上下文（{@link AttributeObjectState} 为 null 或未携带值表）时无法判定 → 放行。</li>
 * </ol>
 * 仅对「本次被提交/修改的从值字段」生效，历史数据不做回溯校验。
 *
 * <p>
 * 结构口径：判定逻辑与 v3 直调完全一致；v3 取数（AdminCache / AttributeInheritHelper / 关系行）经
 * {@link AttributeValueMetadataPort} 收口（{@code v4-bridge-v3} 适配、1:1 镜像 v3 取数语句），本类零 v3 依赖。 允许从值集解析
 * {@link #allowedFromItemKeys} 保持公共方法（业务端 cascade_relations/options 与本校验共用一套口径）。
 */
public final class CascadeValueRangeChecker {

    private CascadeValueRangeChecker() {
    }

    /**
     * 校验从值是否落在主属性当前值的允许集内
     *
     * @param attribute
     *            级联列表属性元数据（24/25）
     * @param fromItemKeys
     *            本次提交的从值 itemKey 列表（已剔除空项）
     * @param objectState
     *            同对象属性值状态（未提供时无法判定 → 放行）
     * @return 违例列表，空列表表示校验通过
     */
    public static List<Violation> checkFromValues(AttributeFacts attribute, List<String> fromItemKeys,
            AttributeObjectState objectState) {
        if (attribute == null || fromItemKeys == null || fromItemKeys.isEmpty()) {
            return List.of();
        }
        if (objectState == null || objectState.objectValueByApiId() == null) {
            // 未提供对象值上下文：无法判定主属性当前值，放行
            return List.of();
        }
        AttributeValueMetadataPort port = AttributeValueMetadataPorts.port();
        MainAttributeView mainAttribute = port.resolveMainAttribute(attribute.id());
        if (mainAttribute == null || mainAttribute.apiId() == null || mainAttribute.apiId().isBlank()) {
            // 主属性配置缺失：由属性配置校验负责，这里放行
            return List.of();
        }
        if (!port.listPresent(mainAttribute.listId())) {
            // 主列表不可用：无法判定允许集，放行
            return List.of();
        }
        // 主属性当前值（itemKey）；主属性被禁用时实例上仍可读到值，不做额外拦截
        Object mainValue = objectState.valueOf(mainAttribute.apiId());
        String mainItemKey = mainValue == null ? null : mainValue.toString().trim();
        if (mainItemKey == null || mainItemKey.isBlank()) {
            // 主值为空 → 从值必须为空
            return List.of(new Violation(SharedKernelResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
                    new Object[]{attribute.name()}));
        }
        Set<String> allowedFromKeys = allowedFromItemKeys(attribute, mainAttribute.listId(), mainItemKey);
        if (allowedFromKeys.isEmpty()) {
            // 主值未登记级联关系或该主值下无启用从值 → 从值必须为空
            return List.of(new Violation(SharedKernelResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
                    new Object[]{attribute.name()}));
        }
        for (String fromItemKey : fromItemKeys) {
            if (fromItemKey == null || fromItemKey.isBlank()) {
                continue;
            }
            if (!allowedFromKeys.contains(fromItemKey.trim())) {
                return List.of(new Violation(SharedKernelResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
                        new Object[]{attribute.name()}));
            }
        }
        return List.of();
    }

    /**
     * 计算主值命中的允许从值 itemKey 集合：级联关系行（type=35，挂级联属性逻辑根）中 helpId=主值id 且从值列表项启用的项。
     *
     * <p>
     * 改造定稿将本方法提升为公共口径（原私有 resolveAllowedFromItemKeys），供业务端 cascade_relations/options 复用，保证两边判定一致。
     *
     * @param cascadeAttribute
     *            级联列表属性元数据
     * @param mainListId
     *            主属性生效枚举列表 id（{@link AttributeValueMetadataPort#resolveMainAttribute} 给出）
     * @param mainItemKey
     *            主属性当前值 itemKey
     * @return 允许从值 itemKey 集合（落库 id 串形态）
     */
    public static Set<String> allowedFromItemKeys(AttributeFacts cascadeAttribute, Long mainListId,
            String mainItemKey) {
        AttributeValueMetadataPort port = AttributeValueMetadataPorts.port();
        ListItemView mainItem = port.listItemByKey(mainListId, mainItemKey);
        Long mainItemId = itemStoreId(mainItem);
        if (mainItemId == null) {
            return Set.of();
        }
        Long cascadeLogicRootId = cascadeAttribute.id() == null ? null : port.logicRootId(cascadeAttribute.id());
        if (cascadeLogicRootId == null) {
            return Set.of();
        }
        List<CascadeRelationRow> relations = port.cascadeMainFromRelations(cascadeLogicRootId);
        if (relations == null || relations.isEmpty()) {
            return Set.of();
        }
        if (cascadeAttribute.listId() == null || !port.listPresent(cascadeAttribute.listId())) {
            return Set.of();
        }
        Set<String> allowedKeys = new LinkedHashSet<>();
        Set<Long> visitedValueIds = new HashSet<>();
        for (CascadeRelationRow relation : relations) {
            if (relation == null || !Objects.equals(relation.helpId(), mainItemId) || relation.value() == null
                    || !visitedValueIds.add(relation.value())) {
                continue;
            }
            ListItemView fromItem = port.itemByKey(cascadeAttribute.listId(), relation.value());
            if (fromItem == null || !EnabledListEnum.ENABLED.getIntKey().equals(fromItem.enabled())) {
                // 只认可启用项（与业务端可选项口径一致）
                continue;
            }
            allowedKeys.add(String.valueOf(itemStoreId(fromItem)));
        }
        return allowedKeys;
    }

    /**
     * 列表项落库id：常规静态列表的 itemKey 即列表项id，取不到时回退节点id（与关系行登记口径一致）
     */
    private static Long itemStoreId(ListItemView item) {
        if (item == null) {
            return null;
        }
        return item.key() != null ? item.key() : item.id();
    }

    /**
     * 从逗号分隔的多值中解析出有效的从值项（级联多选）
     *
     * @param value
     *            提交值
     * @return itemKey 列表
     */
    public static List<String> parseFromItemKeys(Object value) {
        List<String> keys = new ArrayList<>();
        if (value == null || value.toString().isBlank()) {
            return keys;
        }
        for (String token : value.toString().split(",")) {
            if (!token.isBlank()) {
                keys.add(token.trim());
            }
        }
        return keys;
    }
}
