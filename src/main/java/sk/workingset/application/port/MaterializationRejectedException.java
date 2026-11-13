package sk.workingset.application.port;

/** 物化被拒绝：来源快照/目标身份/既有目标与证据不一致。 */
public class MaterializationRejectedException extends RuntimeException {
    public MaterializationRejectedException(String message) {
        super(message);
    }
}
