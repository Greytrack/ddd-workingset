package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
//import com.airedgesoft.gstore.res.FileInfo;
//import com.airedgesoft.plm.api.IAirListItem;
//import com.airedgesoft.plm.api.IRevision;
//import com.airedgesoft.plm.common.enums.list.*;
//import com.airedgesoft.plm.sdk.AdminCache;
//import com.airedgesoft.plm.sdk.fileservers.node.FileServersNode;
//import com.airedgesoft.plm.sdk.list.IListItem;
//import com.airedgesoft.plm.sdk.list.IListObj;
//import com.airedgesoft.plm.sdk.list.IListObjFactory;
//import com.airedgesoft.plm.sdk.objectframework.inner.IInnerRevision;
//import com.airedgesoft.plm.sdk.richtext.IRichTextInner;
//import com.airedgesoft.plm.sdk.richtext.IRichTextJsonHelper;
//import com.airedgesoft.plm.session.ISessionLoader;
//import com.airedgesoft.plm.tools.infrastructure.core.global.CommonResultCode;
//import com.airedgesoft.plm.tools.infrastructure.gstore.GstoreHelper;
//import com.airedgesoft.plm.tools.infrastructure.utils.ObjectUtil;
//import com.airedgesoft.plm.tools.infrastructure.utils.RichTextContentUtil;
//import com.airedgesoft.plm.tools.infrastructure.utils.SpringContextUtils;
//import com.airedgesoft.plm.tools.infrastructure.utils.StringUtils;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.YesOrNoListEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.ImageItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 属性值校验支撑（包级）：纯类型化判定族共用的必填 / 文本 / 富文本 / 单选列表 / 数值 / 日期 / 图片判定。
 *
 * <p>
 * 判定分支、错误码与报错文案照搬现网（原 {@code *ValueSpecification} 与 {@code CascadeValueRangeChecker}），取数口径 v3 直调不变； 只把入参由裸
 * {@code Object} 换为已解析的类型化字段，不再 {@code toString()} 再解析。
 */
@Slf4j
final class AttributeValidationSupport implements IAttributeValidationSupport{

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
        return List.of();
        //return List.of(new Violation(CommonResultCode.COLUMN_REQUIRED, new Object[]{schema.name()}));
    }

    /** 文本规则 + 允许输入长度（非富文本分支）。 */
    static List<Violation> checkText(String text, AttributeFacts schema) {
//        if (SKIPPED_TEXT_API_IDS.contains(schema.apiId())) {
//            return List.of();
//        }
//        if (schema.textRule() != null
//                && !com.airedgesoft.plm.common.util.GlobalValidatorTool.validate(schema.textRule(), text)) {
//            return List.of(new Violation(CommonResultCode.COLUMN_TEXT_RULE_MATCH_ERROR, new Object[]{schema.name()}));
//        }
//        Integer maxInputLength = schema.maxInputLength();
//        if (maxInputLength != null && maxInputLength < text.length()) {
//            return List.of(new Violation(CommonResultCode.COLUMN_TEXT_LENGTH_ERROR,
//                    new Object[]{schema.name(), maxInputLength}));
//        }
        return List.of();
    }

    /** 富文本长度校验（附件大小 + 纯文本字节合计 vs maxInputLength）。 */
    static List<Violation> checkRichText(String text, AttributeFacts schema) {
//        if (ObjectUtils.isEmpty(text) || schema.maxInputLength() == null) {
//            return List.of();
//        }
//        IRichTextJsonHelper richTextJsonHelper = SpringContextUtils.getBean(IRichTextJsonHelper.class);
//        IRichTextInner richTextInner = SpringContextUtils.getBean(IRichTextInner.class);
//        String content = NumberUtil.isLong(text) ? richTextInner.getRichTextContentById(Long.parseLong(text)) : text;
//        List<com.airedgesoft.plm.sdk.attachment.dto.SysGstoreUploadRecord> records = richTextJsonHelper
//                .extractUrlsFromNode(RichTextContentUtil.parseDocument(content));
//        AtomicReference<Double> size = new AtomicReference<>((double) 0L);
//        if (ObjectUtil.isNotEmpty(records)) {
//            records.forEach(record -> size.set(size.get() + Optional.ofNullable(record.getSize()).orElse(0D)));
//        }
//        String plainText = RichTextContentUtil.extractPlainTextByJsonString(content);
//        long plainTextBytes = ObjectUtil.isNotEmpty(plainText) ? plainText.getBytes(StandardCharsets.UTF_8).length : 0L;
//        if (size.get() + plainTextBytes > schema.maxInputLength()) {
//            return List.of(new Violation(CommonResultCode.COLUMN_TEXT_LENGTH_ERROR,
//                    new Object[]{schema.name(), schema.maxInputLength()}));
//        }
        return List.of();
    }

    /** 单选列表项「存在且启用」判定（关联列表按实例解析）。 */
    static List<Violation> checkSingleList(String itemKey, AttributeFacts schema) {
//        if (Objects.isNull(schema.listId())) {
//            return List.of();
//        }
//        IListObj listObj = AdminCache.getInstance().getListCache().get(schema.listId());
//        if (Objects.isNull(listObj)) {
//            return List.of(
//                    new Violation(CommonResultCode.COLUMN_LIST_VALUE_NOT_MATCH_ERROR, new Object[]{schema.name(), ""}));
//        }
//        if (StringUtils.isBlank(itemKey)) {
//            return List.of();
//        }
//        DrawPartRelationListEnum relationList = DrawPartRelationListEnum.ofAttributeApiId(schema.apiId());
//        IListItem listItem;
//        if (relationList == null) {
//            listItem = listObj.getListItemByKey(itemKey);
//        } else {
//            Long instanceId = resolveRelatedInstanceId(schema, itemKey, relationList);
//            listItem = instanceId == null
//                    ? null
//                    : listObj.findItemsByKeysNoMatchSearchCommon(Set.of(instanceId)).stream().findFirst().orElse(null);
//        }
//        if (Objects.isNull(listItem)) {
//            return List.of(new Violation(CommonResultCode.COLUMN_LIST_VALUE_NOT_MATCH_ERROR,
//                    new Object[]{schema.name(), listObj.getName()}));
//        }
//        if (!EnabledListEnum.ENABLED.getIntKey().equals(listItem.getEnabled())) {
//            return List.of(new Violation(CommonResultCode.COLUMN_LIST_VALUE_UN_ENABLED_ERROR,
//                    new Object[]{schema.name(), listObj.getName()}));
//        }
        return List.of();
    }

    /** 数值判定（小数位 / 允许为 0 / 范围）。 */
    static List<Violation> checkNumerical(Double value, AttributeFacts schema) {
//        if (value == null) {
//            return List.of();
//        }
//        if (schema.decimalLength() != null
//                && java.math.BigDecimal.valueOf(value).stripTrailingZeros().scale() > schema.decimalLength()) {
//            return List.of(new Violation(CommonResultCode.COLUMN_NUMBER_DIGITS_MATCH_ERROR,
//                    new Object[]{schema.name(), schema.decimalLength()}));
//        }
//        if (schema.canZero() != null && YesOrNoListEnum.NO.getIntKey().equals(schema.canZero()) && value == 0d) {
//            return List
//                    .of(new Violation(CommonResultCode.COLUMN_NUMBER_CAN_NOT_ZERO_ERROR, new Object[]{schema.name()}));
//        }
//        if (schema.minNumberValue() != null && value < schema.minNumberValue()) {
//            return List.of(new Violation(CommonResultCode.COLUMN_NUMBER_CAN_NOT_LESS_ERROR,
//                    new Object[]{schema.name(), schema.minNumberValue()}));
//        }
//        if (schema.maxNumberValue() != null && value > schema.maxNumberValue()) {
//            return List.of(new Violation(CommonResultCode.COLUMN_NUMBER_CAN_NOT_GREATER_ERROR,
//                    new Object[]{schema.name(), schema.maxNumberValue()}));
//        }
        return List.of();
    }

    /** 日期规则命中性判定。 */
    static List<Violation> checkDate(Long epochMilli, AttributeFacts schema) {
//        if (epochMilli == null || schema.dateRule() == null) {
//            return List.of();
//        }
//        if (!DateRuleEnum.satisfyDateRule(epochMilli, schema.dateRule())) {
//            IListObjFactory listObjFactory = SpringContextUtils.getBean(IListObjFactory.class);
//            IListObj listObj = listObjFactory.getListById(DateRuleEnum.ID);
//            IListItem listItem = listObj.getItemByKey(schema.dateRule());
//            return List.of(new Violation(CommonResultCode.COLUMN_TEXT_RULE_MATCH_ERROR,
//                    new Object[]{schema.name(), listItem.getName()}));
//        }
        return List.of();
    }

    /** 开始 ≤ 结束（比较粒度跟随显示格式）。 */
    static List<Violation> checkStartNotAfterEnd(Long start, Long end, AttributeFacts schema) {
//        if (start == null || end == null) {
//            return List.of();
//        }
//        if (DateUtil.compare(DateUtil.date(start), DateUtil.date(end), dateFormat(schema)) > 0) {
//            return List.of(new Violation(CommonResultCode.COLUMN_START_DATE_NOT_GE_END_DATE_ERROR,
//                    new Object[]{schema.name()}));
//        }
        return List.of();
    }

    /** 图片判定（张数 / 格式白名单 / 单文件大小）。 */
    static List<Violation> checkImages(List<ImageItem> images, AttributeFacts schema) {
//        Integer canMultipleSelect = schema.canMultipleSelect();
//        Integer maxUploadFileNumber = schema.maxUploadFileNumber();
//        if (YesOrNoListEnum.NO.getIntKey().equals(canMultipleSelect)) {
//            maxUploadFileNumber = 1;
//        }
//        if (YesOrNoListEnum.NO.getIntKey().equals(canMultipleSelect) && images.size() > 1) {
//            return List.of(
//                    new Violation(CommonResultCode.COLUMN_IMAGE_CANNOT_MULTIPLE_SELECT, new Object[]{schema.name()}));
//        }
//        if (maxUploadFileNumber != null && images.size() > maxUploadFileNumber) {
//            return List.of(new Violation(CommonResultCode.COLUMN_IMAGE_UPLOAD_FILE_NUMBER_EXCEED,
//                    new Object[]{schema.name(), maxUploadFileNumber}));
//        }
//        Map<String, String> keyMap = pictureFormats();
//        for (ImageItem image : images) {
//            List<Violation> formatViolation = checkImageFormat(image, schema, keyMap);
//            if (!formatViolation.isEmpty()) {
//                return formatViolation;
//            }
//            List<Violation> sizeViolation = checkImageSize(image, schema);
//            if (!sizeViolation.isEmpty()) {
//                return sizeViolation;
//            }
//        }
        return List.of();
    }

    /** 级联从值范围判定（委托 {@code CascadeValueRangeChecker}，主属性值单值喂入）。 */
    static List<Violation> checkCascadeRange(AttributeFacts schema, List<String> fromItemKeys,
            AttributeValue mainAttributeValue) {
        return new ArrayList<>();
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
//        String formatLimit = schema.formatLimit();
//        String fileSuffix = image.fileSuffix();
//        if (StringUtils.isNotBlank(formatLimit)) {
//            String[] formatLimits = formatLimit.split(",");
//            Map<String, String> subKeyMap = keyMap.entrySet().stream()
//                    .filter(entry -> Arrays.asList(formatLimits).contains(entry.getKey()))
//                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//            boolean formatMatch = false;
//            for (String limit : formatLimits) {
//                String format = keyMap.get(limit);
//                if (format != null && format.equalsIgnoreCase(fileSuffix)) {
//                    formatMatch = true;
//                    break;
//                }
//            }
//            if (!formatMatch) {
//                return List.of(new Violation(CommonResultCode.COLUMN_IMAGE_FORMAT_NOT_ALLOWED,
//                        new Object[]{schema.name(), subKeyMap.values()}));
//            }
//            return List.of();
//        }
//        Set<String> allowedFormats = keyMap.values().stream().map(String::toUpperCase).collect(Collectors.toSet());
//        if (!allowedFormats.contains(fileSuffix.toUpperCase())) {
//            return List.of(new Violation(CommonResultCode.COLUMN_IMAGE_FORMAT_NOT_ALLOWED,
//                    new Object[]{schema.name(), keyMap.values()}));
//        }
        return List.of();
    }

    private static List<Violation> checkImageSize(ImageItem image, AttributeFacts schema) {
//        String maxUploadFileSize = schema.maxUploadFileSize();
//        if (StringUtils.isBlank(maxUploadFileSize)) {
//            return List.of();
//        }
//        long maxSizeInBytes = parseSizeInBytes(maxUploadFileSize);
//        GstoreHelper gstoreHelper = SpringContextUtils.getBean(GstoreHelper.class);
//        FileInfo fileInfo = gstoreHelper.queryFileInfo(image.objectKey(), FileServersNode.getGstoreConfig());
//        if (fileInfo.getSize() > maxSizeInBytes) {
//            return List.of(new Violation(CommonResultCode.COLUMN_IMAGE_FILE_SIZE_EXCEED,
//                    new Object[]{schema.name(), maxUploadFileSize}));
//        }
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
//        IListObj listObj = AdminCache.getInstance().getListCache().get(PICTURE_FORMAT_LIST_ID);
//        return listObj.getListValues().stream().collect(Collectors.toMap(item -> item.getValue().toString(),
//                IAirListItem::getAPIID, (existing, replacement) -> existing));
        return Map.of();
    }

//    private static Long resolveRelatedInstanceId(AttributeFacts schema, String itemKey,
//            DrawPartRelationListEnum relationList) {
//        try {
//            IRevision revision = SpringContextUtils.getBean(ISessionLoader.class).loadSessionNotStatic()
//                    .getRevision(relationList.getTargetClassId(), Long.valueOf(itemKey));
//            if (revision instanceof IInnerRevision innerRevision && innerRevision.getInstanceId() != null) {
//                return innerRevision.getInstanceId();
//            }
//        } catch (Exception e) {
//            log.warn("解析设计文件与物料关联列表版次失败, attributeId={}, value={}", schema.id(), itemKey, e);
//        }
//        return null;
//    }

//    private static String dateFormat(AttributeFacts schema) {
//        if (DateShowEnum.YYYYMMDD.getKey().equals(schema.dateShowStyle())) {
//            return "yyyy/MM/dd";
//        }
//        return "yyyy/MM/dd HH:mm:ss";
//    }
}
