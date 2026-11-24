package com.airedgesoft.sharedkernel.businessframework.domain.tracker;

/**
 * Tracker 默认链端口（Shared Kernel 契约）：业务对象创建期解析最终跟踪分类（Tracker id）。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>（勾连模块 bridge-v3 落地后上移）：Tracker 解析是 v3 基建能力（分类元数据 + 工作集缓存）， 不专属任何 BC——需求的创建要用，其余业务对象的创建同样要（旧栈
 * {@code AddAction#prepareAndCheckTracker} 对所有数据对象生效）。 端口留在共享契约，v3 直读实现在 bridge-v3（{@code V3TrackerResolver}）。
 *
 * <p>
 * 行为契约（照录旧栈 {@code AddAction#preCheckCommonParam} / {@code prepareAndCheckTracker}，2026-09 考古）：
 *
 * <ul>
 * <li><b>工作集解析链</b>：显式入参 → 线程上下文 → 分类不在工作集时兜底默认工作集 1L → 仍空报「对象工作集不能为空」。</li>
 * <li><b>未传 trackerId（默认链）</b>：产品空间为空或分类挂全局 Tracker → 全局 Tracker；否则经工作集取产品空间默认分支且须可见； 仍无 → 「{分类名}的默认跟踪分类不存在」。</li>
 * <li><b>已传 trackerId（校验链）</b>：分支须存在；分类挂全局 Tracker 时<b>强制覆盖</b>为全局 Tracker（旧栈同款）； 否则须落在当前工作集的产品空间分支内（可见），不在 →
 * 「{分支名}不存在工作集{工作集}中」。</li>
 * </ul>
 */
public interface TrackerService {

    /**
     * 解析创建期的最终 Tracker id。
     *
     * @param workingsetId
     *            工作集 id（可空，走解析链）
     * @param workSpace
     *            产品空间（可空——为空时走全局 Tracker 分支，与旧栈一致）
     * @param trackerId
     *            入参 Tracker id（可空——为空走默认链）
     * @param type
     *            业务对象分类 type
     * @return 最终 Tracker id（非 null）
     * @throws com.airedgesoft.lib.exception.DomainException
     *             工作集缺失、分支不存在、分类未挂全局 Tracker 且默认分支不可见、或分支不在工作集产品空间内
     */
    Long resolve(Long workingsetId, Long workSpace, Long trackerId, Long type);
}
