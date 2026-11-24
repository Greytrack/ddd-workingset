package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.util.Objects;

/**
 * 值对象：目标版次（TargetRevision，引用稿 §五-4 的两态形态）。
 *
 * <p>
 * 「跟随最新」（指针语义、参与传播怀疑）／「钉住某版次」（固定版次、传播类型「不传播」）——不用 {@code null} 兼职（口径 18）。 不可变；替换发生在宿主聚合内部（换目标版次＝删旧行＋加新行，无 set）。
 *
 * @param mode
 *            两态（非空）
 * @param revision
 *            钉住的版次号（{@code PINNED} 时必填；{@code FOLLOW_LATEST} 必须为空——fail-fast 守住形态）
 */
public record TargetRevision(TargetRevisionMode mode, Integer revision) {

    public TargetRevision {
        Objects.requireNonNull(mode, "mode");
        if (mode == TargetRevisionMode.PINNED && (revision == null || revision < 1)) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_TARGET_REVISION_REQUIRED, revision);
        }
        if (mode == TargetRevisionMode.FOLLOW_LATEST && revision != null) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT,
                    SharedKernelResultCode.REFERENCE_TARGET_REVISION_FORBIDDEN, revision);
        }
    }

    /**
     * 跟随最新（指针语义）。
     *
     * @return 跟随最新形态
     */
    public static TargetRevision followLatest() {
        return new TargetRevision(TargetRevisionMode.FOLLOW_LATEST, null);
    }

    /**
     * 钉住某版次。
     *
     * @param revision
     *            目标版次号（≥1）
     * @return 钉住形态
     */
    public static TargetRevision pinnedTo(int revision) {
        return new TargetRevision(TargetRevisionMode.PINNED, revision);
    }

    /** 是否跟随最新（决定传播类型装配：跟随最新取命令携带值；钉住＝不传播，口径 5）。 */
    public boolean isFollowLatest() {
        return mode == TargetRevisionMode.FOLLOW_LATEST;
    }
}
