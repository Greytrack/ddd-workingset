package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import cn.hutool.core.date.DateUtil;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.EnabledListEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.YesOrNoListEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.ImageItem;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeValueMetadataPort.ListItemView;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeValueMetadataPort.RichTextUsage;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性值校验支撑（包级）：纯类型化判定族共用的必填 / 文本 / 富文本 / 单选列表 / 数值 / 日期 / 图片判定。
 *
 * <p>
 * 判定分支、错误码与报错文案照搬现网（原 {@code *ValueSpecification} 与 {@code CascadeValueRangeChecker}）； v3 平台取数经
 * {@link AttributeValueMetadataPort} 收口（{@code v4-bridge-v3} 适配、1:1 镜像 v3 取数语句），本类零 v3 依赖。
 */
@Slf4j
final class AttributeValidationSupport implements IAttributeValidationSupport {

    /** 图片格式字典列表 id（air_sys_list_obj.id=10017，v3 硬编码口径）。 */
    private static final Long PICTURE_FORMAT_LIST_ID = 10017L;

    /** 密码字段跳过文本内容校验（v3 硬编码名单）。 */
    private static final List<String> SKIPPED_TEXT_API_IDS = List.of("Password");

    private AttributeValidationSupport() {
    }

    /** 必填判定（元数据 required＝是）。 */
    static boolean required(AttributeFacts schema) {
        return schema.required();
    }

    /** 必填违例。 */
    static List<Violation> requiredViolation(AttributeFacts schema) {
        return List.of(new Violation(SharedKernelResultCode.COLUMN_REQUIRED, new Object[]{schema.name()}));
    }

    /** 文本规则 + 允许输入长度（非富文本分支）。 */
    static List<Violation> checkText(String text, AttributeFacts schema) {
        if (SKIPPED_TEXT_API_IDS.contains(schema.apiId())) {
            return List.of();
        }
        if (schema.textRule() != null && !AttributeValueMetadataPorts.port().textRuleValid(schema.textRule(), text)) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_TEXT_RULE_MATCH_ERROR, new Object[]{schema.name()}));
        }
        Integer maxInputLength = schema.maxInputLength();
        if (maxInputLength != null && maxInputLength < text.length()) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_TEXT_LENGTH_ERROR,
                    new Object[]{schema.name(), maxInputLength}));
        }
        return List.of();
    }

    /** 富文本长度校验（附件大小 + 纯文本字节合计 vs maxInputLength）。 */
    static List<Violation> checkRichText(String text, AttributeFacts schema) {
        if (text == null || text.isEmpty() || schema.maxInputLength() == null) {
            return List.of();
        }
        RichTextUsage usage = AttributeValueMetadataPorts.port().richTextUsage(text);
        String plainText = usage.plainText();
        long plainTextBytes = plainText != null && !plainText.isEmpty()
                ? plainText.getBytes(StandardCharsets.UTF_8).length
                : 0L;
        if (usage.attachmentBytes() + plainTextBytes > schema.maxInputLength()) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_TEXT_LENGTH_ERROR,
                    new Object[]{schema.name(), schema.maxInputLength()}));
        }
        return List.of();
    }

    /** 单选列表项「存在且启用」判定（关联列表按实例解析）。 */
    static List<Violation> checkSingleList(String itemKey, AttributeFacts schema) {
        if (Objects.isNull(schema.listId())) {
            return List.of();
        }
        AttributeValueMetadataPort port = AttributeValueMetadataPorts.port();
        if (!port.listPresent(schema.listId())) {
            return List.of(
                    new Violation(SharedKernelResultCode.COLUMN_LIST_VALUE_NOT_MATCH_ERROR, new Object[]{schema.name(), ""}));
        }
        if (itemKey == null || itemKey.isBlank()) {
            return List.of();
        }
        Long targetClassId = port.relationTargetClassId(schema.apiId());
        ListItemView listItem;
        if (targetClassId == null) {
            listItem = port.listItemByKey(schema.listId(), itemKey);
        } else {
            Long instanceId = resolveRelatedInstanceId(schema, itemKey, targetClassId);
            listItem = instanceId == null ? null : port.listItemByInstanceKeys(schema.listId(), Set.of(instanceId));
        }
        if (Objects.isNull(listItem)) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_LIST_VALUE_NOT_MATCH_ERROR,
                    new Object[]{schema.name(), port.listName(schema.listId())}));
        }
        if (!EnabledListEnum.ENABLED.getIntKey().equals(listItem.enabled())) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_LIST_VALUE_UN_ENABLED_ERROR,
                    new Object[]{schema.name(), port.listName(schema.listId())}));
        }
        return List.of();
    }

    /** 数值判定（小数位 / 允许为 0 / 范围）。 */
    static List<Violation> checkNumerical(Double value, AttributeFacts schema) {
        if (value == null) {
            return List.of();
        }
        if (schema.decimalLength() != null
                && java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale() > schema.decimalLength()) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_NUMBER_DIGITS_MATCH_ERROR,
                    new Object[]{schema.name(), schema.decimalLength()}));
        }
        if (schema.canZero() != null && YesOrNoListEnum.NO.getIntKey().equals(schema.canZero()) && value == 0d) {
            return List
                    .of(new Violation(SharedKernelResultCode.COLUMN_NUMBER_CAN_NOT_ZERO_ERROR, new Object[]{schema.name()}));
        }
        if (schema.minNumberValue() != null && value < schema.minNumberValue()) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_NUMBER_CAN_NOT_LESS_ERROR,
                    new Object[]{schema.name(), schema.minNumberValue()}));
        }
        if (schema.maxNumberValue() != null && value > schema.maxNumberValue()) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_NUMBER_CAN_NOT_GREATER_ERROR,
                    new Object[]{schema.name(), schema.maxNumberValue()}));
        }
        return List.of();
    }

    /** 日期规则命中性判定。 */
    static List<Violation> checkDate(Long epochMilli, AttributeFacts schema) {
        if (epochMilli == null || schema.dateRule() == null) {
            return List.of();
        }
        if (!AttributeValueMetadataPorts.port().dateRuleSatisfied(epochMilli, schema.dateRule())) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_TEXT_RULE_MATCH_ERROR,
                    new Object[]{schema.name(),
                            AttributeValueMetadataPorts.port().dateRuleItemName(schema.dateRule())}));
        }
        return List.of();
    }

    /** 开始 ≤ 结束（比较粒度跟随显示格式）。 */
    static List<Violation> checkStartNotAfterEnd(Long start, Long end, AttributeFacts schema) {
        if (start == null || end == null) {
            return List.of();
        }
        if (DateUtil.compare(DateUtil.date(start), DateUtil.date(end), dateFormat(schema)) > 0) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_START_DATE_NOT_GE_END_DATE_ERROR,
                    new Object[]{schema.name()}));
        }
        return List.of();
    }

    /** 图片判定（张数 / 格式白名单 / 单文件大小）。 */
    static List<Violation> checkImages(List<ImageItem> images, AttributeFacts schema) {
        Integer canMultipleSelect = schema.canMultipleSelect();
        Integer maxUploadFileNumber = schema.maxUploadFileNumber();
        if (YesOrNoListEnum.NO.getIntKey().equals(canMultipleSelect)) {
            maxUploadFileNumber = 1;
        }
        if (YesOrNoListEnum.NO.getIntKey().equals(canMultipleSelect) && images.size() > 1) {
            return List.of(
                    new Violation(SharedKernelResultCode.COLUMN_IMAGE_CANNOT_MULTIPLE_SELECT, new Object[]{schema.name()}));
        }
        if (maxUploadFileNumber != null && images.size() > maxUploadFileNumber) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_IMAGE_UPLOAD_FILE_NUMBER_EXCEED,
                    new Object[]{schema.name(), maxUploadFileNumber}));
        }
        Map<String, String> keyMap = pictureFormats();
        for (ImageItem image : images) {
            List<Violation> formatViolation = checkImageFormat(image, schema, keyMap);
            if (!formatViolation.isEmpty()) {
                return formatViolation;
            }
            List<Violation> sizeViolation = checkImageSize(image, schema);
            if (!sizeViolation.isEmpty()) {
                return sizeViolation;
            }
        }
        return List.of();
    }

    /** 级联从值范围判定（委托 {@code CascadeValueRangeChecker}，主属性值单值喂入）。 */
    static List<Violation> checkCascadeRange(AttributeFacts schema, List<String> fromItemKeys,
            AttributeValue mainAttributeValue) {
        return CascadeValueRangeChecker.checkFromValues(schema, fromItemKeys,
                objectStateOf(schema, mainAttributeValue));
    }

    private static AttributeObjectState objectStateOf(AttributeFacts schema, AttributeValue mainAttributeValue) {
        if (mainAttributeValue == null || schema.mainApiId() == null) {
            return null;
        }
        // 主属性作用域已在分类模型装配期解析（AttributeFacts#mainApiId），此处不再调用 v3 作用域解析工具
        return AttributeObjectState.of(Map.of(schema.mainApiId(), mainAttributeValue.toText()));
    }

    private static List<Violation> checkImageFormat(ImageItem image, AttributeFacts schema,
            Map<String, String> keyMap) {
        String formatLimit = schema.formatLimit();
        String fileSuffix = image.fileSuffix();
        if (formatLimit != null && !formatLimit.isBlank()) {
            String[] formatLimits = formatLimit.split(",");
            Map<String, String> subKeyMap = keyMap.entrySet().stream()
                    .filter(entry -> Arrays.asList(formatLimits).contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            boolean formatMatch = false;
            for (String limit : formatLimits) {
                String format = keyMap.get(limit);
                if (format != null && format.equalsIgnoreCase(fileSuffix)) {
                    formatMatch = true;
                    break;
                }
            }
            if (!formatMatch) {
                return List.of(new Violation(SharedKernelResultCode.COLUMN_IMAGE_FORMAT_NOT_ALLOWED,
                        new Object[]{schema.name(), subKeyMap.values()}));
            }
            return List.of();
        }
        Set<String> allowedFormats = keyMap.values().stream().map(String::toUpperCase).collect(Collectors.toSet());
        if (!allowedFormats.contains(fileSuffix.toUpperCase())) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_IMAGE_FORMAT_NOT_ALLOWED,
                    new Object[]{schema.name(), keyMap.values()}));
        }
        return List.of();
    }

    private static List<Violation> checkImageSize(ImageItem image, AttributeFacts schema) {
        String maxUploadFileSize = schema.maxUploadFileSize();
        if (maxUploadFileSize == null || maxUploadFileSize.isBlank()) {
            return List.of();
        }
        long maxSizeInBytes = parseSizeInBytes(maxUploadFileSize);
        long fileSize = AttributeValueMetadataPorts.port().fileSize(image.objectKey());
        if (fileSize > maxSizeInBytes) {
            return List.of(new Violation(SharedKernelResultCode.COLUMN_IMAGE_FILE_SIZE_EXCEED,
                    new Object[]{schema.name(), maxUploadFileSize}));
        }
        return List.of();
    }

    private static long parseSizeInBytes(String maxUploadFileSize) {
        String size = maxUploadFileSize.toUpperCase().trim();
        try {
            if (size.endsWith("MB")) {
                return Long.parseLong(size.replace("MB", "").trim()) * 1024 * 1024;
            }
            if (size.endsWith("M")) {
                return Long.parseLong(size.replace("M", "").trim()) * 1024 * 1024;
            }
            return Long.parseLong(size) * 1024 * 1024;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Map<String, String> pictureFormats() {
        return AttributeValueMetadataPorts.port().listValueApiIds(PICTURE_FORMAT_LIST_ID);
    }

    private static Long resolveRelatedInstanceId(AttributeFacts schema, String itemKey, Long targetClassId) {
        try {
            return AttributeValueMetadataPorts.port().instanceIdOfRevision(targetClassId, Long.valueOf(itemKey));
        } catch (Exception e) {
            log.warn("解析设计文件与物料关联列表版次失败, attributeId={}, value={}", schema.id(), itemKey, e);
        }
        return null;
    }

    private static String dateFormat(AttributeFacts schema) {
        if (AttributeValueMetadataPorts.port().dateOnlyStyle(schema.dateShowStyle())) {
            return "yyyy/MM/dd";
        }
        return "yyyy/MM/dd HH:mm:ss";
    }
}
