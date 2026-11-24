package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 属性值校验取数端口：校验族（{@link AttributeValidationSupport} / {@link CascadeValueRangeChecker}）所需的 v3 平台取数收口。
 *
 * <p>
 * 存在原因：v3 直连（AdminCache / IListObjFactory / ISessionLoader / GstoreHelper / SpringContextUtils 等）在 domain 层被分层门禁禁止
 * （deploy-full {@code LayeredArchitectureRulesTest} 规则 4 / 规则 6）。本端口把原直连取数语句 1:1 镜像为强类型方法，返回 v4 自有视图，v3 类型零外泄；
 * 方法语义（含空值 / 缺失行为）与 v3 直调逐条对齐。
 *
 * <p>
 * 实现位置：{@code v4-bridge-v3}（{@code V3AttributeValueMetadataAdapter}，唯一可直连 v3 的模块）， 由装配期安装进
 * {@link AttributeValueMetadataPorts}（静态持有者，同 {@code AuthorizationPorts} 约定）；未安装即抛，避免静默降级。
 */
public interface AttributeValueMetadataPort {

    // ==================== 列表字典（v3 AdminCache.getInstance().getListCache()） ====================

    /** 列表是否存在（v3 {@code listCache.get(listId) != null}） */
    boolean listPresent(Long listId);

    /** 列表名（v3 {@code IListObj#getName()}）；列表不存在返回 {@code null} */
    String listName(Long listId);

    /** 列表项按 key（v3 {@code IListObj#getListItemByKey(String)}）；列表或项不存在返回 {@code null} */
    ListItemView listItemByKey(Long listId, String key);

    /** 列表项按 key（v3 {@code IListObj#getItemByKey(Long)}）；列表或项不存在返回 {@code null} */
    ListItemView itemByKey(Long listId, Long key);

    /** 列表项按实例 id 集合的首个命中（v3 {@code IListObj#findItemsByKeysNoMatchSearchCommon(Set)}）；无命中返回 {@code null} */
    ListItemView listItemByInstanceKeys(Long listId, Set<Long> keys);

    /** 列表值 → apiId 映射（v3 {@code IListObj#getListValues()}，{@code value.toString() → getAPIID()}，重复取首个） */
    Map<String, String> listValueApiIds(Long listId);

    // ==================== 文本 / 日期规则（v3 GlobalValidatorTool / DateRuleEnum / DateShowEnum） ====================

    /** 文本规则命中性（v3 {@code GlobalValidatorTool.validate(textRule, text)}） */
    boolean textRuleValid(Long textRule, String text);

    /** 日期规则命中性（v3 {@code DateRuleEnum.satisfyDateRule(epochMilli, dateRule)}） */
    boolean dateRuleSatisfied(Long epochMilli, Long dateRule);

    /** 日期规则列表项名（v3 {@code IListObjFactory.getListById(DateRuleEnum.ID).getItemByKey(dateRule).getName()}） */
    String dateRuleItemName(Long dateRule);

    /** 显示格式是否「仅日期」（v3 {@code DateShowEnum.YYYYMMDD.getKey().equals(dateShowStyle)}） */
    boolean dateOnlyStyle(Long dateShowStyle);

    // ==================== 关联列表与版次（v3 DrawPartRelationListEnum / ISessionLoader） ====================

    /**
     * 属性关联列表的目标分类 id（v3 {@code DrawPartRelationListEnum.ofAttributeApiId(apiId)} → {@code getTargetClassId()}）； 非关联列表返回
     * {@code null}
     */
    Long relationTargetClassId(String attributeApiId);

    /**
     * 版次 → 实例 id（v3 {@code ISessionLoader.loadSessionNotStatic().getRevision(classId, revisionId)} +
     * {@code IInnerRevision#getInstanceId()}）；非内部版次或无实例返回 {@code null}
     */
    Long instanceIdOfRevision(Long targetClassId, Long revisionId);

    // ==================== 图片文件（v3 GstoreHelper） ====================

    /** 文件大小（v3 {@code GstoreHelper.queryFileInfo(objectKey, FileServersNode.getGstoreConfig()).getSize()}） */
    long fileSize(String objectKey);

    // ==================== 富文本（v3 IRichTextInner / IRichTextJsonHelper / RichTextContentUtil） ====================

    /**
     * 富文本用量（v3 口径：{@code storedText} 为纯数字时按富文本 id 解析内容，附件字节合计 + 纯文本抽取）
     */
    RichTextUsage richTextUsage(String storedText);

    // ==================== 级联元数据（v3 AdminCache / AttributeInheritHelper） ====================

    /**
     * 级联属性的主属性作用域副本（v3 {@code AdminCache.getNodeById(cascadeAttributeId)} →
     * {@code AttributeInheritHelper.resolveScopedAttribute(mainAttributeId, helpId)}）， 并携带主属性生效枚举列表 id（v3
     * {@code resolveMainList} 的「逻辑根优先」口径）。级联节点或主属性解析失败返回 {@code null}
     */
    MainAttributeView resolveMainAttribute(Long cascadeAttributeId);

    /** 属性逻辑根 id（v3 {@code AttributeInheritHelper.logicRootId}）；节点缺失或无逻辑根返回 {@code null} */
    Long logicRootId(Long attributeId);

    /** 级联主从关系行（v3 {@code AdminCache.getRelationshipsByTypeAndParent(ATTRIBUTE_CASCADE_MAIN_FROM, logicRootId)}）；无关系返回空列表 */
    List<CascadeRelationRow> cascadeMainFromRelations(Long logicRootId);

    /** 列表项视图（v3 {@code IListItem} 只读投影；{@code key}＝{@code getItemKey()}，{@code id}＝{@code getId()}） */
    record ListItemView(Long key, Long id, Integer enabled) {
    }

    /** 富文本用量视图：{@code attachmentBytes}＝附件字节合计（缺失按 0），{@code plainText}＝纯文本（可为空串 / {@code null}） */
    record RichTextUsage(double attachmentBytes, String plainText) {
    }

    /** 级联主属性视图：{@code apiId}＝主属性 apiId，{@code listId}＝主属性生效枚举列表 id（可为 {@code null}） */
    record MainAttributeView(String apiId, Long listId) {
    }

    /** 级联关系行视图（v3 {@code SysRelationship} 只读投影） */
    record CascadeRelationRow(Long helpId, Long value) {
    }
}
