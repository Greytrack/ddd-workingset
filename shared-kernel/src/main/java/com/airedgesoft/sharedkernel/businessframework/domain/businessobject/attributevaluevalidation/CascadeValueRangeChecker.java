//package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;
//
//import com.airedgesoft.plm.common.constants.IRelationShipType;
//import com.airedgesoft.plm.common.enums.list.EnabledListEnum;
//import com.airedgesoft.plm.sdk.AdminCache;
//import com.airedgesoft.plm.sdk.list.IListItem;
//import com.airedgesoft.plm.sdk.list.IListObj;
//import com.airedgesoft.plm.sdk.objectframework.dto.BaseNode;
//import com.airedgesoft.plm.sdk.objectframework.dto.SysRelationship;
//import com.airedgesoft.plm.sdk.objectframework.node.AttributeInheritHelper;
//import com.airedgesoft.plm.sdk.objectframework.node.AttributeNode;
//import com.airedgesoft.plm.tools.infrastructure.core.global.CommonResultCode;
//import com.airedgesoft.plm.tools.infrastructure.utils.StringUtils;
//import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
//
//import java.util.*;
//
///**
// * 级联列表属性「从值范围」校验（原旧栈 {@code CascadeValueRangeChecker} 整体平移，取数口径 v3 直调不变）。
// *
// * <p>
// * 规则（与业务端 cascade_relations/options 口径一致）：
// * <ol>
// * <li>主属性当前值为空 → 从值必须为空；</li>
// * <li>主值有值但未登记级联关系、或该主值下无「启用」的从值 → 从值必须为空；</li>
// * <li>否则从值必须属于该主值下已登记且启用的从值集合（级联单选=单值、级联多选=逗号逐项）；</li>
// * <li>主属性被禁用不影响本校验（实例上的值仍可读取）；</li>
// * <li>调用方未提供对象值上下文（{@link AttributeObjectState} 为 null 或未携带值表）时无法判定 → 放行。</li>
// * </ol>
// * 仅对「本次被提交/修改的从值字段」生效，历史数据不做回溯校验。
// *
// * <p>
// * 与旧栈的两点结构差异（判定逻辑不变）：上下文参数由 {@code CheckedContext} 改为 {@link AttributeObjectState}； 允许从值集解析
// * {@link #allowedFromItemKeys} 按改造定稿提升为公共方法（业务端 cascade_relations/options 与本校验共用一套口径）。
// */
//public final class CascadeValueRangeChecker {
//
//    private CascadeValueRangeChecker() {
//    }
//
//    /**
//     * 校验从值是否落在主属性当前值的允许集内
//     *
//     * @param attribute
//     *            级联列表属性节点（24/25）
//     * @param fromItemKeys
//     *            本次提交的从值 itemKey 列表（已剔除空项）
//     * @param objectState
//     *            同对象属性值状态（未提供时无法判定 → 放行）
//     * @return 违例列表，空列表表示校验通过
//     */
//    public static List<Violation> checkFromValues(AttributeFacts attribute, List<String> fromItemKeys,
//            AttributeObjectState objectState) {
//        if (attribute == null || fromItemKeys == null || fromItemKeys.isEmpty()) {
//            return List.of();
//        }
//        if (objectState == null || objectState.objectValueByApiId() == null) {
//            // 未提供对象值上下文：无法判定主属性当前值，放行
//            return List.of();
//        }
//        AttributeNode cascadeNode = nodeOf(attribute);
//        AttributeNode mainAttribute = cascadeNode == null ? null : resolveMainAttribute(cascadeNode);
//        if (mainAttribute == null || StringUtils.isBlank(mainAttribute.getAPIID())) {
//            // 主属性配置缺失：由属性配置校验负责，这里放行
//            return List.of();
//        }
//        IListObj mainList = resolveMainList(mainAttribute);
//        if (mainList == null) {
//            // 主列表不可用：无法判定允许集，放行
//            return List.of();
//        }
//        // 主属性当前值（itemKey）；主属性被禁用时实例上仍可读到值，不做额外拦截
//        Object mainValue = objectState.valueOf(mainAttribute.getAPIID());
//        String mainItemKey = mainValue == null ? null : mainValue.toString().trim();
//        if (StringUtils.isBlank(mainItemKey)) {
//            // 主值为空 → 从值必须为空
//            return List.of(new Violation(CommonResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
//                    new Object[]{attribute.name()}));
//        }
//        Set<String> allowedFromKeys = allowedFromItemKeys(attribute, mainList, mainItemKey);
//        if (allowedFromKeys.isEmpty()) {
//            // 主值未登记级联关系或该主值下无启用从值 → 从值必须为空
//            return List.of(new Violation(CommonResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
//                    new Object[]{attribute.name()}));
//        }
//        for (String fromItemKey : fromItemKeys) {
//            if (StringUtils.isBlank(fromItemKey)) {
//                continue;
//            }
//            if (!allowedFromKeys.contains(fromItemKey.trim())) {
//                return List.of(new Violation(CommonResultCode.CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR,
//                        new Object[]{attribute.name()}));
//            }
//        }
//        return List.of();
//    }
//
//    /**
//     * 解析级联属性主属性节点：mainAttributeId 持久化为逻辑根id，按级联属性所属层解析到当前可用副本
//     */
//    private static AttributeNode resolveMainAttribute(AttributeNode cascadeAttribute) {
//        // mainAttributeId 持久化为逻辑根id（copy-on-write 引用约定）；resolveScopedAttribute 按级联属性所属层解析到当前可用副本
//        Long mainAttributeId = cascadeAttribute.getMainAttributeId();
//        return AttributeInheritHelper.resolveScopedAttribute(mainAttributeId, cascadeAttribute.getHelpId());
//    }
//
//    /**
//     * 主列表（主属性逻辑根引用的列表）
//     */
//    private static IListObj resolveMainList(AttributeNode mainAttribute) {
//        Long logicRootId = AttributeInheritHelper.logicRootId(mainAttribute);
//        BaseNode rootNode = logicRootId == null ? null : AdminCache.getNodeById(logicRootId);
//        Long enumList = rootNode instanceof AttributeNode rootAttribute
//                ? rootAttribute.getEnumList()
//                : mainAttribute.getEnumList();
//        return enumList == null ? null : AdminCache.getInstance().getListCache().get(enumList);
//    }
//
//    /**
//     * 计算主值命中的允许从值 itemKey 集合：级联关系行（type=35，挂级联属性逻辑根）中 helpId=主值id 且从值列表项启用的项。
//     *
//     * <p>
//     * 改造定稿将本方法提升为公共口径（原私有 resolveAllowedFromItemKeys），供业务端 cascade_relations/options 复用，保证两边判定一致。
//     */
//    public static Set<String> allowedFromItemKeys(AttributeFacts cascadeAttribute, IListObj mainList,
//            String mainItemKey) {
//        IListItem mainItem = mainList.getListItemByKey(mainItemKey);
//        Long mainItemId = itemStoreId(mainItem);
//        if (mainItemId == null) {
//            return Set.of();
//        }
//        AttributeNode cascadeNode = nodeOf(cascadeAttribute);
//        Long cascadeLogicRootId = cascadeNode == null ? null : AttributeInheritHelper.logicRootId(cascadeNode);
//        if (cascadeLogicRootId == null) {
//            return Set.of();
//        }
//        List<SysRelationship> relations = AdminCache
//                .getRelationshipsByTypeAndParent(IRelationShipType.ATTRIBUTE_CASCADE_MAIN_FROM, cascadeLogicRootId);
//        if (relations == null || relations.isEmpty()) {
//            return Set.of();
//        }
//        IListObj fromList = cascadeAttribute.listId() == null
//                ? null
//                : AdminCache.getInstance().getListCache().get(cascadeAttribute.listId());
//        if (fromList == null) {
//            return Set.of();
//        }
//        Set<String> allowedKeys = new LinkedHashSet<>();
//        Set<Long> visitedValueIds = new HashSet<>();
//        for (SysRelationship relation : relations) {
//            if (relation == null || !Objects.equals(relation.getHelpId(), mainItemId) || relation.getValue() == null
//                    || !visitedValueIds.add(relation.getValue())) {
//                continue;
//            }
//            IListItem fromItem = fromList.getItemByKey(relation.getValue());
//            if (fromItem == null || !EnabledListEnum.ENABLED.getIntKey().equals(fromItem.getEnabled())) {
//                // 只认可启用项（与业务端可选项口径一致）
//                continue;
//            }
//            allowedKeys.add(String.valueOf(itemStoreId(fromItem)));
//        }
//        return allowedKeys;
//    }
//
//    /**
//     * 列表项落库id：常规静态列表的 itemKey 即列表项id，取不到时回退节点id（与关系行登记口径一致）
//     */
//    /** 元数据节点（共享内核内部仍读 v3 元数据树；对外签名只暴露 v4 自有模型）。 */
//    private static AttributeNode nodeOf(AttributeFacts definition) {
//        return definition == null || definition.id() == null
//                ? null
//                : AdminCache.getNodeById(definition.id()) instanceof AttributeNode node ? node : null;
//    }
//
//    private static Long itemStoreId(IListItem item) {
//        if (item == null) {
//            return null;
//        }
//        return item.getItemKey() != null ? item.getItemKey() : item.getId();
//    }
//
//    /**
//     * 从逗号分隔的多值中解析出有效的从值项（级联多选）
//     *
//     * @param value
//     *            提交值
//     * @return itemKey 列表
//     */
//    public static List<String> parseFromItemKeys(Object value) {
//        List<String> keys = new ArrayList<>();
//        if (value == null || StringUtils.isBlank(value.toString())) {
//            return keys;
//        }
//        for (String token : value.toString().split(",")) {
//            if (StringUtils.isNotBlank(token)) {
//                keys.add(token.trim());
//            }
//        }
//        return keys;
//    }
//}
