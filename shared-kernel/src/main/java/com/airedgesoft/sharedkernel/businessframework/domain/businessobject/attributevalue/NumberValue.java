package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.plm.tools.infrastructure.utils.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 值对象：编号（NUMBER 属性种类的属性值；v3 {@code NumberValueObject} 的 v4 自有投影——投影先例见 {@code AttributeFacts}）。
 *
 * <p>
 * <b>三形态一载体</b>（v3 编号引擎三分支同口径）：
 *
 * <ul>
 * <li>引擎形态（空实例）：走编号引擎自动取号；</li>
 * <li>手输形态：纯文本号（占用查重、字符合法性守卫适用）；</li>
 * <li>段配置形态：编号组前缀 / 段配置 / 后缀 / 自定义参数（引擎按段生成）。</li>
 * </ul>
 *
 * <p>
 * <b>wire 入口（转换点唯一）</b>：REST 一律 JSON string，{@link #fromText(String)} 是「串 → 编号值」的**唯一**转换点—— 空白 → 引擎形态；JSON <b>对象</b> →
 * 段配置形态（v3 {@code DynamicAddReq#getNumberValueObject} 口径：只有 JSON 对象才视为编号段配置）； 其余 → 手输形态。JSON 嗅探与解析只此一处 （此前
 * {@code RequirementCommandService#isManualNumber} 与 bridge 双重解析的泄漏就此消除）。
 *
 * <p>
 * 只读载体：不做校验、不做 IO（查重与字符合法性归编号域 {@code NumberService}）。
 */
public record NumberValue(String plainText, String groupPrefixNumber, Map<Long, Object> numberConfig,
        String groupSuffixNumber, Map<String, Object> customParam) implements AttributeValue {

    /** 引擎形态（空实例）：走编号引擎自动取号。 */
    public static NumberValue engine() {
        return new NumberValue(null, null, Map.of(), null, Map.of());
    }

    /** 手输形态：纯文本号。 */
    public static NumberValue fromPlainText(String number) {
        return new NumberValue(number, null, Map.of(), null, Map.of());
    }

    /** 段配置形态：编号组前缀 / 段配置（段 id → 取值）/ 后缀 / 自定义参数（可空项归一为空值）。 */
    public static NumberValue fromSegment(String groupPrefixNumber, Map<Long, Object> numberConfig,
            String groupSuffixNumber, Map<String, Object> customParam) {
        return new NumberValue(null, groupPrefixNumber == null ? "" : groupPrefixNumber,
                numberConfig == null ? Map.of() : Map.copyOf(numberConfig),
                groupSuffixNumber == null ? "" : groupSuffixNumber,
                customParam == null ? Map.of() : Map.copyOf(customParam));
    }

    /**
     * wire 入口：REST 串形态 → 编号值（转换点唯一，见类注释）。
     *
     * @param wire
     *            空白（走引擎取号）/ JSON 对象串（段配置）/ 其余（手输号）
     * @return 编号值
     * @throws com.airedgesoft.lib.exception.DomainException
     *             JSON 对象串结构非法（错误码由编号域统一映射）
     */
    public static NumberValue fromText(String wire) {
        if (wire == null || wire.isBlank()) {
            return engine();
        }
        String raw = wire.trim();
        if (isSegmentJson(raw)) {
            return fromWireSegment(raw);
        }
        return fromPlainText(raw);
    }

    /** 手输形态判定（占用查重只对手输号有意义——引擎号 / 段生成号由引擎保证唯一）。 */
    public boolean isPlainText() {
        return plainText != null;
    }

    /** 是否引擎形态（空实例）。 */
    public boolean isEngine() {
        return plainText == null && numberConfig.isEmpty();
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.NUMBER;
    }

    @Override
    public boolean isEmpty() {
        return isEngine();
    }

    /**
     * 串形态：手输回纯文本；段配置回 v3 wire JSON（只含 {@code groupPrefixNumber}/{@code numberConfig}/
     * {@code groupSuffixNumber}/{@code customParam} 四个字段，不暴露值对象内部形态）；引擎形态空串。
     */
    @Override
    public String toText() {
        if (isPlainText()) {
            return plainText;
        }
        if (isEngine()) {
            return "";
        }
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("groupPrefixNumber", groupPrefixNumber);
        wire.put("numberConfig", numberConfig);
        wire.put("groupSuffixNumber", groupSuffixNumber);
        if (!customParam.isEmpty()) {
            wire.put("customParam", customParam);
        }
        return JsonUtil.toJson(wire);
    }

    // ------------------------------------------------------------------
    // wire 解析（JSON 依赖只此一处）
    // ------------------------------------------------------------------

    /** JSON 对象判定（v3 {@code DynamicAddReq#getNumberValueObject} 口径：只有 JSON 对象才视为段配置）。 */
    private static boolean isSegmentJson(String raw) {
        try {
            return JsonUtil.getInstance().readTree(raw).isObject();
        } catch (Exception exception) {
            return false;
        }
    }

    /** JSON 对象串 → 段配置形态（结构非法抛格式错误，文案沿用编号域「编号格式非法」口径）。 */
    private static NumberValue fromWireSegment(String raw) {
        try {
            com.fasterxml.jackson.databind.JsonNode tree = JsonUtil.getInstance().readTree(raw);
            Map<Long, Object> config = new LinkedHashMap<>();
            com.fasterxml.jackson.databind.JsonNode rawConfig = tree.get("numberConfig");
            if (rawConfig != null && rawConfig.isObject()) {
                rawConfig.properties().forEach(entry -> config.put(Long.valueOf(entry.getKey()),
                        entry.getValue().isValueNode() ? box(entry.getValue()) : entry.getValue()));
            }
            return fromSegment(textOrNull(tree.get("groupPrefixNumber")), config,
                    textOrNull(tree.get("groupSuffixNumber")), castCustomParam(tree.get("customParam")));
        } catch (NumberFormatException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new com.airedgesoft.lib.exception.DomainException(
                    com.airedgesoft.sharedkernel.SharedKernelResultCode.NUMBER_RULE_ID_INVALID, raw);
        }
    }

    /** JSON 值节点 → Java 值（文本 / 数值 / 布尔；其余原样交 JsonNode）。 */
    private static Object box(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }

    private static String textOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castCustomParam(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> params = new LinkedHashMap<>();
        node.properties().forEach(entry -> params.put(entry.getKey(),
                entry.getValue().isValueNode() ? box(entry.getValue()) : entry.getValue()));
        return params;
    }
}
