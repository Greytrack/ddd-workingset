package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification;


import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassScope;

import java.util.List;

/** 读取授权所需分类与页签元数据。 */
public interface AuthorizationMetadataReader {

    /** 分类作用域：基类 ID 与参与匹配的分类 ID 集合（对象自身分类 + 父类 + 基类）。 */
    ClassScope classScope(Long classId);

    /** 该权限类型是否为工作流状态级权限。 */
    boolean isStatusPrivilege(int privilege);

    /** 分类所属基类 ID。 */
    Long baseClassId(Long classId);

    /** 分类下全部属性 ID。 */
    List<Long> attributeIds(Long classId);

    /** 页签继承链上的父页签 ID；无继承时为空。 */
    Long tabInherit(Long tableId);
}
