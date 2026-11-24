package com.airedgesoft.sharedkernel.businessframework.domain.history;

/**
 * 操作历史记录端口（Shared Kernel 契约）：业务对象关键动作落「操作历史」。
 *
 * <p>
 * <b>为何在共享契约</b>：操作历史是 v3 基建能力（{@code air_sys_history} + i18n 模板），跨 BC 通用； v3 直读/直写在
 * bridge-v3（{@code V3OperationHistoryRecorder}，经 sdk 层 {@code IHistoryService} 写入）。
 *
 * <p>
 * <b>调用时机</b>：由各 BC 在领域事件的事务提交后监听调用（v3 口径：创建 / 删除等动作各写一条历史）， 保证「业务成功才留痕」；实现不得抛异常影响主流程（失败仅记日志）。
 */
public interface OperationHistoryRecorder {

    /**
     * 记录一条「创建」历史。
     *
     * @param type
     *            业务对象分类 type（v3 口径写 {@code operateNodeId}）
     * @param instanceId
     *            实例 id
     * @param number
     *            业务编号（历史条目展示名；可空时由实现兜底）
     */
    void recordCreated(Long type, Long instanceId, String number);
}
