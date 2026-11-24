package com.airedgesoft.sharedkernel.businessframework.domain.admin.tracker;

import lombok.Data;

/**
 * 分支（Tracker）成员权限快照（替代 v3 {@code TrackerPermissionDTO}）。
 *
 * <p>
 * 判权只读「目标实例 + 是否可看 / 可改 + 是否对子项生效」。
 * </p>
 */
@Data
public class TrackerPermissionSnapshot {

    /** 被授权的实例 ID */
    private Long instanceId;

    /** 与实例关联的成员对象 ID */
    private Long relatedInstanceId;

    /** 是否可见（1/0） */
    private Integer canView;

    /** 是否可改（1/0） */
    private Integer canEdit;

    /** 是否对所有子项生效（1/0） */
    private Integer applyToAllSubItems;

    /** v3 语义：有修改权限则一定有查看权限 */
    public boolean canView() {
        return isYes(canView) || isYes(canEdit);
    }

    /** v3 语义：是否可改 */
    public boolean canEdit() {
        return isYes(canEdit);
    }

    /** v3 语义：是否对所有子项生效 */
    public boolean applyToAllSubItems() {
        return isYes(applyToAllSubItems);
    }

    private static boolean isYes(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }
}
