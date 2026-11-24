package com.airedgesoft.sharedkernel.businessframework.domain.admin.role;

import lombok.Data;

import java.util.List;

/** 角色快照（替代 v3 {@code RoleNode}）：判权只需要启用状态与权限 ID 列表。 */
@Data
public class RoleSnapshot {

    private Long id;

    private String apiId;

    private String name;

    private Long enabled;

    private List<Long> privileges;

    public boolean isEnabled() {
        return enabled == null || enabled == 1L;
    }
}
