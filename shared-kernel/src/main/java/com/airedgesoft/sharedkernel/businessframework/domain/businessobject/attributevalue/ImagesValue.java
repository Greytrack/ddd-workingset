package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.airedgesoft.plm.common.enums.list.AttributeTypeEnum;
import com.airedgesoft.plm.tools.infrastructure.utils.JsonUtil;

import java.util.List;

/**
 * 值对象：图片集合（纯类型化字段，权威为 {@link #images}）。
 *
 * <p>
 * 非结构化的图片串解析失败时按空集合处理（v3 口径容忍），由必填 / 张数规则在策略侧兜底。
 */
public record ImagesValue(List<ImageItem> images) implements AttributeValue {

    public ImagesValue {
        images = images == null ? List.of() : List.copyOf(images);
    }

    /** 串形态构造（JSON 数组串；空值归一为空实例）。 */
    public static ImagesValue fromText(String text) {
        if (text == null || text.isBlank()) {
            return empty();
        }
        List<ImageItem> items;
        try {
            items = JsonUtil.parseArray(text, ImageItem.class);
        } catch (Exception e) {
            items = List.of();
        }
        return new ImagesValue(items);
    }

    /** 空实例。 */
    public static ImagesValue empty() {
        return new ImagesValue(List.of());
    }

    @Override
    public AttributeTypeEnum type() {
        return AttributeTypeEnum.IMAGES;
    }

    @Override
    public boolean isEmpty() {
        return images.isEmpty();
    }

    @Override
    public String toText() {
        return images.isEmpty() ? "" : JsonUtil.toJson(images);
    }
}
