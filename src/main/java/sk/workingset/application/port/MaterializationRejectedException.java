package sk.workingset.application.port;

import sk.common.error.AppException;
import sk.common.error.WorkingsetResultCode;

/** 物化被拒绝：来源快照/目标身份/既有目标与证据不一致。 */
public class MaterializationRejectedException extends AppException {
    public MaterializationRejectedException(String message) {
        super(WorkingsetResultCode.MATERIALIZATION_REJECTED, message, null);
    }
}
