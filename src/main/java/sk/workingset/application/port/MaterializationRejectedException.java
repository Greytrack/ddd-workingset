package sk.workingset.application.port;

public class MaterializationRejectedException extends RuntimeException {
    public MaterializationRejectedException(String message) {
        super(message);
    }
}
