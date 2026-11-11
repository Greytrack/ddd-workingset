package sk.workingset.application;

import java.util.Objects;

/**
 * 应用层失败（携带 T4 九分类中的适用子集）；调用方可据此区分「不存在」与「未纳入」，两者不得合并。
 */
public class ApplicationFailure extends RuntimeException {

    private final FailureReason reason;

    public ApplicationFailure(FailureReason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public FailureReason reason() {
        return reason;
    }
}
