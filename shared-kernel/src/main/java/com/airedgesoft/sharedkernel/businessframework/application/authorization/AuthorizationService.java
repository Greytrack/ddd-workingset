package com.airedgesoft.sharedkernel.businessframework.application.authorization;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObjectDto;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.ObjectAttributePermission;

import java.util.List;

/**
 * 授权判定共享应用服务端口（方案 A 上提至 shared-kernel，由 {@code v4-identityaccess} 实现）。
 *
 * <p>
 * 以显式用户作为主体执行操作权限、属性读写权限及工作流状态流转权限判定。
 */
public interface AuthorizationService {

    /** 判断用户是否拥有与对象无关的权限类型。 */
    boolean hasPrivilege(Long userId, int privilege);

    /** 查询指定权限的应用范围，工具权限允许对象为空。 */
    List<Long> getAppliedTos(Long userId, int privilege, BusinessObjectDto object);

    /** 批量查询对象属性的读写权限。 */
    List<ObjectAttributePermission> getObjectAttributePermissions(Long userId, int privilege,
            List<BusinessObjectDto> objects, boolean includeWritable);

    /** 查询当前状态下有权限流转到的目标状态。 */
    List<Long> getPrivilegeStatusList(Long userId, int privilege, Long workflowId, BusinessObjectDto object,
            Long fromStatus);

    /** 判断指定用户对业务对象是否拥有权限。 */
    boolean hasPrivilege(Long userId, int privilege, BusinessObjectDto object);

    /** 判断业务对象指定页签的操作权限。 */
    boolean hasPrivilege(Long userId, int privilege, BusinessObjectDto object, Long tableId);
}
