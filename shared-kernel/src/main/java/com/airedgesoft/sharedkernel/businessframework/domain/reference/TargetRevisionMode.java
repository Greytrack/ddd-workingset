package com.airedgesoft.sharedkernel.businessframework.domain.reference;

/**
 * 枚举：目标版次两态（TargetRevisionMode，引用稿约束 §五-4）。
 *
 * <p>
 * 「跟随最新」＝指针、参与传播怀疑；「钉住某版次」＝固定版次、传播类型装配为「不传播」（口径 4）。 不用 {@code null} 兼职，也不用两个字段拼。
 */
public enum TargetRevisionMode {

    /** 跟随最新：目标始终指向其最新版次；参与传播怀疑。 */
    FOLLOW_LATEST,

    /** 钉住版次：固定到挂出时的目标版次；传播类型「不传播」。 */
    PINNED
}
