package com.airedgesoft.lib.jdbc.common;

/** air_inst_common 表的持久化记录。 */
public record AirInstCommonPo(
        Long id, Long baseClass, Long clazz, Long type, Long workSpace, String number, String name,
        Long status, String version, Long revision, Integer deleteFlag, Long lockVersion,
        String createdBy, Long createdTime, String updatedBy, Long updatedTime, Integer checkedOut,
        Long owner, Long commonId, Long workingSetId, Long parentId, Long tracker, Long maxRevision
) {
}
