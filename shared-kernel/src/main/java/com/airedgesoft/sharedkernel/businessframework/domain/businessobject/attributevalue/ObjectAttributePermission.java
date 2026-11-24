package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue;
import java.util.List;

/** 单个对象的属性权限结果。 */
public record ObjectAttributePermission(Long classId, Long bussinessObjectId, List<Long> readableAttrIds,
        List<Long> writableAttrIds) {
    public ObjectAttributePermission {
        readableAttrIds = List.copyOf(readableAttrIds);
        writableAttrIds = List.copyOf(writableAttrIds);
    }
}
