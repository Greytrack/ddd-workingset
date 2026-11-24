package com.airedgesoft.sharedkernel.businessframework.domain.admin.role;

import java.util.List;

/** 查询主体当前生效的角色。 */
public interface UserRoleReader {

    List<Long> findRoleIds(Long userId);

    List<Long> findEnabledRoleIds(Long userId);
}
