package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RawValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeTypeFactory;

import java.util.*;

/**
 * 字段值装配（共享领域服务 · 与 {@link PlacementService} 同构：无状态、纯装配、不编排不落库）。
 *
 * <p>
 * <b>职责</b>（创建 / 修改两条路径共用，原 {@code RequirementCommandService} 内联逻辑上提——业务对象通用知识，任何 BC 复用）： 「provided 列值 + 分类视图 →
 * {@code List<FieldValue>}」——主属性优先排序（级联先主后从）→ 逐字段经属性工厂「转换＋校验」（无 schema 经 {@link RawValue} 放行）→ 多选列表 itemKey 规范化（v3
 * {@code AddAction#getItemKey} 口径）→ 级联主值注入（存量⊕变更）。
 *
 * <p>
 * <b>框架纪律</b>：shared-kernel 领域层「业务对象」包零框架注解（同 {@code PlacementService}），装配由各 BC 的 {@code infrastructure/config} 以
 * {@code @Bean} 声明。
 *
 * <p>
 * <b>契约边界</b>：本服务只依赖共享内核的只读契约（{@link ClassificationFacts} / {@link AttributeFacts}）与
 * {@link ListItemKeyResolver}——分类元数据的来源、投影、缓存全在 bridge-v3，本类不依赖任何 v3 类型。
 */
public class FieldValueService {

    /** 列表项 itemKey 解析契约（多选列表规范化取数；可空——无元数据环境多选按原值放行）。 */
    private final ListItemKeyResolver itemKeyResolver;

    public FieldValueService(ListItemKeyResolver itemKeyResolver) {
        this.itemKeyResolver = itemKeyResolver;
    }

    /**
     * 创建期装配：键＝落库列名的 provided 值 → 类型化字段值（空值不入参——交给分类默认值与必填装配）。
     *
     * <p>
     * 排序规则：主属性列（级联的「主」）前置，其余按传入序；编号 / 父边 / 跟踪分类等聚合根一级字段由调用方在装配后原样追加。
     *
     * @param provided
     *            provided 值（键＝落库列名；null / 空白串不入参）
     * @param classification
     *            分类视图（属性定义 + 主属性列）
     * @return 类型化字段值（不可变）
     */
    public List<FieldValue> forCreation(Map<String, Object> provided, ClassificationFacts classification) {
        Set<String> mainColumns = classification.mainColumns();
        Map<String, AttributeValue> known = new LinkedHashMap<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        provided.forEach((column, value) -> expandStartAndEndDate(normalized, column, value));
        List<FieldValue> values = new ArrayList<>(normalized.size());
        normalized.entrySet().stream().filter(entry -> hasValue(entry.getValue()))
                .sorted(Comparator.comparingInt(entry -> mainColumns.contains(entry.getKey()) ? 0 : 1))
                .forEach(entry -> values.add(
                        typed(entry.getKey(), normalizeMultipleList(entry.getValue(), classification, entry.getKey()),
                                classification, known)));
        return List.copyOf(values);
    }

    /**
     * 起止日期入参展开（v3 {@code START_AND_END_DATE} 线口径）：{@code startdate01="起~止"} → {@code startdate01=起} ⊕
     * {@code endstartdate01=止}（v3 真表也是两列）；非本形态原样保留。
     */
    private static void expandStartAndEndDate(Map<String, Object> target, String column, Object value) {
        String text = value == null ? null : String.valueOf(value);
        if (column != null && column.startsWith("startdate") && text != null && text.contains("~")) {
            String[] range = text.split("~", 2);
            target.put(column, range[0].trim());
            target.put("end" + column, range[1].trim());
            return;
        }
        target.put(column, value);
    }

    /**
     * 修改期装配：变更（键＝落库列名）⊕ 存量（级联主值判定）→ 类型化字段值。
     *
     * @param changes
     *            变更（键＝落库列名 → 串形态新值）
     * @param existing
     *            已生效存量（共享字段 ⊕ 版次字段，键＝apiId 或列名；供级联主属性当前值判定）
     * @param classification
     *            分类视图
     * @return 类型化字段值（不可变）
     */
    public List<FieldValue> forModification(Map<String, String> changes, Map<String, AttributeValue> existing,
            ClassificationFacts classification) {
        Set<String> mainColumns = classification.mainColumns();
        Map<String, AttributeValue> known = new LinkedHashMap<>();
        if (existing != null) {
            known.putAll(existing);
        }
        List<FieldValue> values = new ArrayList<>(changes.size());
        changes.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> mainColumns.contains(entry.getKey()) ? 0 : 1))
                .forEach(entry -> values.add(typed(entry.getKey(), entry.getValue(), classification, known)));
        return List.copyOf(values);
    }

    /** 单字段装配：有属性定义时经属性工厂「转换＋校验」（生成即校验，命中首条即抛），无定义时经 {@link RawValue} 放行。 */
    private FieldValue typed(String column, Object provided, ClassificationFacts classification,
            Map<String, AttributeValue> known) {
        AttributeFacts definition = classification.byColumn(column).orElse(null);
        String text = String.valueOf(provided);
        if (definition == null) {
            return FieldValue.of(column, new RawValue(text));
        }
        AttributeValue value = AttributeTypeFactory.createAttributeValue(definition, text,
                mainValue(definition, classification, known));
        known.put(column, value);
        return FieldValue.of(column, value);
    }

    /**
     * 多选列表入参规范化（#26，v3 {@code createObjectBySubClassAndMap} 口径）：逗号串 → 逐个转 itemKey → 去重保序。
     *
     * <p>
     * 仅对「有 schema 且配置了列表」的多选属性生效；契约未装配或无列表时保留原值（由值对象做切分与去重）。
     */
    private Object normalizeMultipleList(Object provided, ClassificationFacts classification, String column) {
        AttributeFacts definition = classification.byColumn(column).orElse(null);
        if (itemKeyResolver == null || definition == null || provided == null || !definition.multipleList()
                || definition.listId() == null) {
            return provided;
        }
        String raw = String.valueOf(provided);
        if (raw.isBlank()) {
            return provided;
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                keys.add(itemKeyResolver.toItemKey(definition.listId(), token.trim()));
            }
        }
        return String.join(",", keys);
    }

    /** 空值（null / 空白串）不入创建入参（交给分类默认值与必填装配）。 */
    private static boolean hasValue(Object value) {
        return value != null && !(value instanceof String text && text.isBlank());
    }

    /** 级联字段的主属性当前值（非级联 / 主属性无值恒 null）：主属性 apiId 经分类视图双索引解析为列名（BC 词表不进共享内核）。 */
    private static AttributeValue mainValue(AttributeFacts definition, ClassificationFacts classification,
            Map<String, AttributeValue> known) {
        if (definition == null || definition.mainApiId() == null) {
            return null;
        }
        String mainColumn = classification.byApiId(definition.mainApiId()).map(AttributeFacts::column).orElse(null);
        return mainColumn == null ? null : known.get(mainColumn);
    }
}
