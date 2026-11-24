package com.airedgesoft.lib.restful.etag;

import java.util.List;

/**
 * 批量写命令（逐条携带并发版本）。
 *
 * <p>
 * 框架校验顺序：先整体校验所有条目的 {@code lockedVersion}（任一条缺失 → 400），再进入业务事务；任一条版本不符 → 409，整批不落库（规范 §4.1 / §8.5）。
 */
public interface BatchLockedCommand {

    /** 逐条更新 / 状态变更项。 */
    List<? extends IdentifiedLockedCommand> items();
}
