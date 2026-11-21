package sk.common.error;

/**
 * 公共持久化失败码。
 */
public enum PersistenceResultCode implements DomainResCode {
    VERSION_CONFLICT(601, "乐观锁异常，请刷新重试");

    private final int code;
    private final String desc;

    PersistenceResultCode(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public DomainErrorType getType() {
        return DomainErrorType.ABORTED;
    }
}
