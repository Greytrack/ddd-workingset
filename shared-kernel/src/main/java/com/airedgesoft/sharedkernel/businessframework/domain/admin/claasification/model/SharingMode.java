package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model;

/**
 * 分类共享策略（v3 {@code classifications.sharing_mode}）。
 *
 * <p>
 * 原为 {@code workingset.domain.Classification} 的内部枚举；该值对象由 {@link ClassSnapshot} 取代后，本枚举落到分类元数据模型侧， 与
 * {@link ClassSnapshot#isShared()} 同侧——共享策略本就是分类的元数据事实，不是工作集绑定自有概念。
 */
public enum SharingMode {

    /**
     * 分类允许共享：绑定须为 SHARED。
     */
    SHARED,

    /**
     * 分类不允许共享：仅接受 UNSHARED 绑定。
     */
    UNSHARED;

    /**
     * 分类声明的策略是否接受所需绑定模式：请求 SHARED 恒被接受，其余仅当与分类策略一致时接受。
     *
     * @param requested
     *            请求的共享模式
     * @return true=相容
     */
    public boolean allows(SharingMode requested) {
        return requested == SHARED || requested == this;
    }
}
