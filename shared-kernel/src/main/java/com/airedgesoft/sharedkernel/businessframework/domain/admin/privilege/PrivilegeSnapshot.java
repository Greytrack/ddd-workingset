package com.airedgesoft.sharedkernel.businessframework.domain.admin.privilege;

import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * 权限配置快照（替代 v3 的 {@code PrivilegeSnapshot}）。
 *
 * <p>
 * 方法名与原件保持一致（{@code getAPIID()} 等），使迁入代码的调用点零改动； 只保留判权实际读取的字段，v3
 * 的校验/转换（{@code verify}、{@code nodeToProperty}）等配置维护能力不迁入。
 */
@Data
public class PrivilegeSnapshot {

    /** 权限 ID */
    private Long id;

    /** 权限 API ID */
    private String apiId;

    /** 权限名称 */
    private String name;

    /** 权限类型（按钮类型） */
    private Integer privilegeType;

    /** 作用基础类 ID */
    private Long baseClassId;

    /** 作用分类 ID（系统权限为空） */
    private Long classId;

    /** 作用页签 ID */
    private Long tableId;

    /** 关联工作流 ID */
    private Long workflowId;

    /** 数据条件 ID */
    private Long criteriaId;

    /** 起始状态 */
    private Long fromStatus;

    /** 目标状态 */
    private List<Long> toStatus;

    /** 生效范围（appliedTo） */
    private List<Long> appliedTos;

    /** 拥有该权限的角色 ID */
    private List<Long> roleIds;

    /** 是否启用：1 启用，0 停用（与 v3 {@code Node#enabled} 同义） */
    private Long enabled;

    /** 与 v3 {@code Node#getAPIID()} 同名，保持调用点零改动 */
    public String getAPIID() {
        return apiId;
    }

    /** 与 v3 {@code Node#getEnabled()} 同语义：缺省视为启用 */
    public Long getEnabled() {
        if (Objects.isNull(enabled)) {
            enabled = 1L;
        }
        return enabled;
    }

    /** 与 v3 {@code Node#isEnabled()} 同名，保持调用点零改动 */
    public boolean isEnabled() {
        return getEnabled() == null || Objects.equals(getEnabled(), 1L);
    }

}
