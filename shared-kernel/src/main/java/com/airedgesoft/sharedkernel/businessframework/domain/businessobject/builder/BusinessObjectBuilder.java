package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder;

import com.airedgesoft.lib.exception.ResCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.RawValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.Violation;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable;

import java.util.*;

/**
 * 业务对象构造器（能力基接口）。
 *
 * <p>
 * 对应 14 需求建模 / 08 多版次能力 新类图 {@code <<interface 构造类 · 能力基接口>>}（2026-09-23 定稿：工厂 → 构造器 Builder 模式）：
 *
 * <pre>
 * class BusinessObjectBuilder {
 *     + build(createDto) BusinessObject
 *     + buildBusinessObject(createDto) BusinessObject
 * }
 * </pre>
 *
 * <p>
 * <b>build＝default 装配编排（模板编排保留在基接口）</b>：{@code buildBusinessObject} → 实现了 {@link RevisionBuilder} 则
 * {@code buildRevisionItem}＋挂载（{@code Revisionable#addRevision}，挂载即指向）→ 实现了 VersionBuilder 则
 * {@code buildVersion}（版本构造能力，细则见 09，能力面随版本轮落）—— <b>能力未实现即跳过对应步骤</b>：单版次壳（如同行评审）只实现基接口，天然不建版次/版本。
 *
 * <p>
 * <b>设计演进记录（postBuild 钩子删除 · 用户拍板 2026-09-23）</b>：新类图原样有 {@code postBuild(businessObject)} 后置钩子；
 * 评审中裁定「先全部删除」——创建期系统字段装配（编号 / Tracker 默认链）由具体构造器在 {@code buildRevisionItem}（初始版次构造）内完成 （见各业务构造器），模板只保留「建主对象 →
 * 建版次＋挂载」两步；后续若确需统一后置扩展点再议。
 *
 * <p>
 * <b>创建期公共装配动作（用户拍板 2026-10-13：由具体构造器上移到本基接口）</b>：与分类无关的创建装配动作收敛为 default， 具体构造器只留业务点（身份 / 一级字段 / 版次内容 / 本 BC 错误码）：
 *
 * <ul>
 * <li>{@link #fillRequiredDefaults(Collection, Map)}——必填元数据默认值填充（元数据默认值 / 日期类当前时刻）；</li>
 * <li>{@link #missingRequiredFields(Collection, Map)}——必填属性缺失清单（创建可见＋必填＋未传值）；</li>
 * <li>{@link #firstViolationMessage(Violation)}——首条违例 → 报错文案（旧栈 {@code DataObjectTools.validate} 占位口径）；</li>
 * <li>{@link #longFieldValue(CreateInput, String)}——创建入参结构字段的数值提取（编号 / 跟踪分类 / 父边等）。</li>
 * </ul>
 *
 * <p>
 * <b>与旧抽象工厂（{@code BusinessObjectFactory}）的差异</b>：① 模板协作者收敛——{@code revisionFactory()} 与 {@code attachInitialRevision}
 * 两个 SPI 删除，版次构造能力并入 {@link RevisionBuilder}（原独立版次工厂并入具体构造器）、 挂载直接走 {@code Revisionable#addRevision}；②
 * {@code create/postCreate} 更名 {@code build/buildBusinessObject}。
 *
 * @param <T>
 *            聚合根类型
 */
public interface BusinessObjectBuilder<T extends BusinessObject> {

    /**
     * 系统装配属性 apiId（分类 / 工作空间 / 所有者 / 编号）：由创建装配通道写入聚合骨架， 不参与必填校验与默认值填充（旧栈 {@code addVisibleCheck} 的
     * {@code apiIdsExcludes} 原样）。
     */
    Set<String> SYSTEM_ASSEMBLY_API_IDS = Set.of("Type", "WorkSpace", "Owner", "Number");

    /** 元数据「是」位（旧栈 {@code YesOrNoListEnum.YES} 的 int key）。 */
    int META_FLAG_YES = 1;

    /**
     * 模板方法：装配完整业务对象（含初始版次，按能力面可选）。 充当builder中的导演模式
     *
     * <p>
     * 流程：{@code buildBusinessObject} →（实现 {@link RevisionBuilder} 时）{@code buildRevisionItem} ＋ 挂载即指向。
     */
    default T build(CreateInput createInput) {
        // 1. 构建聚合主对象
        T businessObject = buildBusinessObject(createInput);

        // 2. 构建版次对象
        if (this instanceof RevisionBuilder<?, ?> revisionBuilder
                && businessObject instanceof Revisionable revisionableBusinessObject) {
            revisionableBusinessObject.mountRevision(revisionBuilder.buildRevisionItem(createInput));
        }
        // 3.后续如果有版本版次

        // 4.构建工作流，有可能会发事件
        return businessObject;
    }

    /** 建主对象本身（不含版次）。 */
    T buildBusinessObject(CreateInput createInput);

    // ------------------------------------------------------------------
    // 创建期公共装配动作（default；分类无关，具体构造器按需复用）
    // ------------------------------------------------------------------

    /**
     * 创建期必填元数据默认值填充（旧栈 {@code fillRequiredAttributesWithMetadataDefaults} → {@code tryPutMetadataDefaultForCreate} 口径）：
     *
     * <ul>
     * <li>候选：创建可见＋必填＋不在系统装配属性集（与 {@link #missingRequiredFields(Collection, Map)} 同过滤）</li>
     * <li>只读属性不填（{@code readOnly}）；关联源属性过滤挂账（sdk 层 {@code AttributeNode} 未暴露 source）</li>
     * <li>当前值非空 → 保留入参；为空 → 经 {@link AttributeDefaultValueResolver#resolveForCreate}（日期=当前时刻、日期区间=「时刻~时刻」、其余=元数据默认值），
     * 解析结果非空才写入</li>
     * <li>富文本特判挂账（旧栈对 RICH_TEXT 空值跳过填充；v4 规约族暂无富文本类型分派）</li>
     * </ul>
     *
     * <p>
     * schema 为 null / 空（分类元数据源未接入、重建口径）时原样返回入参——创建期不填充。
     *
     * @param fieldValues
     *            创建入参字段值（可空）
     * @param schemas
     *            分类属性 schema（apiId → AttributeNode；可空＝不填充）；与字段匹配用元数据 {@code dbName}（＝业务数据列名）
     * @return 填充后的字段值集合（新集合；入参不变）
     */
    default Collection<FieldValue> fillRequiredDefaults(Collection<FieldValue> fieldValues,
            Map<String, AttributeFacts> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return fieldValues == null ? List.<FieldValue>of() : fieldValues;
        }
        Collection<FieldValue> filled = new ArrayList<>();
        if (fieldValues != null) {
            filled.addAll(fieldValues);
        }
        for (AttributeFacts node : schemas.values()) {
            String column = node.column();
            if (!isRequiredDefaultCandidate(node) || column == null || column.isBlank()) {
                continue;
            }
            Optional<AttributeValue> current = filled.stream().filter(field -> field.column().equals(column))
                    .map(FieldValue::value).findFirst();
            if (current.isPresent() && current.get() != null && !current.get().isEmpty()) {
                continue;
            }
            filled.removeIf(field -> field.column().equals(column));
            Object defaultValue = node.defaultValueForCreate();
            if (defaultValue != null) {
                // 键即落库列名（元数据 dbName 在手）；无属性元数据口径的裸值经 RawValue 兜底
                filled.add(FieldValue.of(column, new RawValue(defaultValue)));
            }
        }
        return filled;
    }

    /**
     * 必填属性缺失清单（旧栈 {@code addVisibleCheck} 口径）：创建可见＋必填＋入参未出现该 apiId 即入清单 （值是否为空由属性值校验按类型裁决）。
     *
     * <p>
     * 收集式——调用方取首项转本 BC 的「必填属性未填写」错误码；schema 为 null（元数据源未接入）时视为无缺失。
     *
     * @param fieldValues
     *            创建入参字段值（可空）
     * @param schemas
     *            分类属性 schema（apiId → AttributeNode；可空＝不校验）；存在性按元数据 {@code dbName}（＝业务数据列名）判定
     * @return 缺失的必填属性节点（空列表即通过）
     */
    default List<AttributeFacts> missingRequiredFields(Collection<FieldValue> fieldValues,
            Map<String, AttributeFacts> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return List.of();
        }
        Set<String> present = new LinkedHashSet<>();
        if (fieldValues != null) {
            for (FieldValue fieldValue : fieldValues) {
                present.add(fieldValue.column());
            }
        }
        List<AttributeFacts> missing = new ArrayList<>();
        for (AttributeFacts node : schemas.values()) {
            String column = node.column();
            if (column == null || column.isBlank()) {
                continue;
            }
            if (!SYSTEM_ASSEMBLY_API_IDS.contains(node.apiId()) && node.addVisible() && node.required()
                    && !present.contains(column)) {
                missing.add(node);
            }
        }
        return missing;
    }

    /**
     * 首条违例 → 报错文案：旧栈 {@code DataObjectTools.validate} 的占位分支照搬—— 1 个占位格式化 1 个、2 个格式化 2 个、其余原样取描述。
     */
    default String firstViolationMessage(Violation violation) {
        ResCode code = violation.code();
        Object[] args = violation.args() == null ? new Object[0] : violation.args();
        if (args.length == 1) {
            return code.getDesc().formatted(args[0]);
        }
        if (args.length == 2) {
            return code.getDesc().formatted(args[0], args[1]);
        }
        return code.getDesc();
    }

    /**
     * 创建入参结构字段提取：指定**落库列名**的长整型值（未传或非数值返回 {@code null}）。
     *
     * <p>
     * 用途：创建装配期从业务数据通道取出「聚合根一级字段 / 结构字段」（编号 / 跟踪分类 / 父边等）的数值形态。
     */
    default Long longFieldValue(CreateInput createInput, String column) {
        if (createInput == null || column == null) {
            return null;
        }
        return createInput.fieldValues().stream()
                .filter(field -> field.column().equals(column) && field.value() != null && !field.value().isEmpty())
                .map(field -> asLongOrNull(field.value().toText())).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** 默认值填充候选：创建可见＋必填＋只读排除＋非系统装配属性。 */
    private boolean isRequiredDefaultCandidate(AttributeFacts node) {
        return !SYSTEM_ASSEMBLY_API_IDS.contains(node.apiId()) && node.addVisible() && node.required()
                && !node.readOnly();
    }

    /** 宽松数值化：Number 直取，字符串可解析才取，其余 null（结构字段容错口径）。 */
    private static Long asLongOrNull(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
