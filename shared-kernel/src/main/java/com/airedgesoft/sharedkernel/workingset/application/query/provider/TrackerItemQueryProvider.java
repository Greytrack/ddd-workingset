package com.airedgesoft.sharedkernel.workingset.application.query.provider;

import com.airedgesoft.sharedkernel.workingset.application.TrackerItemContent;

/**
 * 只读公共契约（T4 §3.1）：由业务 BC 的 Adapter 实现；SK 只依赖该接口。
 *
 * <p>该接口按「一个变体」实例化（构造时绑定 {@code LocalId}），不提供写方法、整体校验或提交。
 */
public interface TrackerItemQueryProvider {

    /**
     * 按业务版次读取完整内容（BC 内部重建）；版次不可读时须显式失败，不得用当前值冒充。
     */
    TrackerItemContent readAt(Integer revision);

    /**
     * 既有派生依赖可回读已标记删除的来源内容。
     */
    default TrackerItemContent readInheritedAt(Integer revision) {
        return readAt(revision);
    }

    default ContentEvidence evidence() {
        throw new UnsupportedOperationException("comparison evidence is unavailable");
    }

    record ContentEvidence(Long commonId, Integer revision, String version, long lockVersion, boolean deleted) {
    }

}
