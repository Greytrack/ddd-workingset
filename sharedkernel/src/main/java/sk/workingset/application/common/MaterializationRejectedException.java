package sk.workingset.application.common;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

/** 物化被拒绝：来源快照/目标身份/既有目标与证据不一致。 */
public class MaterializationRejectedException extends DomainException {
    public MaterializationRejectedException(String message) {
        super(WorkingsetResultCode.MATERIALIZATION_REJECTED, message, null);
    }
}
