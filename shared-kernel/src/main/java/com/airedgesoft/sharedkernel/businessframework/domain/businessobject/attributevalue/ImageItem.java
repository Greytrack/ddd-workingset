package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * 值对象（嵌套）：图片条目。
 *
 * <p>
 * {@code fileSuffix} 自 {@code objectKey} 派生（v3 {@code ImageValueObject} 同口径），不单独落值。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageItem(String objectKey, String fileName, String previewUrl, String downloadUrl) {

    public ImageItem {
        Objects.requireNonNull(objectKey, "objectKey");
    }

    /** 文件后缀（自 objectKey 派生）。 */
    public String fileSuffix() {
        int dot = objectKey.lastIndexOf('.');
        return dot < 0 ? "" : objectKey.substring(dot + 1);
    }
}
