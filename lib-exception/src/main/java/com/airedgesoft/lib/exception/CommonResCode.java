package com.airedgesoft.lib.exception;

/**
 * 框架通用错误码（跨 BC 共享）：与具体业务无关、由基础设施 / 框架层抛出的错误语义。
 *
 * <p>
 * <b>并发冲突（409）在此统一</b>：乐观锁冲突（CAS 未命中）与 body {@code lockedVersion} 不一致是同一语义——资源已被他人修改， 客户端需刷新后重试。版本已移入 body，不再是 RFC 9110
 * 条件请求失败，故统一 409（偏离 D11 反转）。 持久化层（lib-jdbc）与协议层（lib-restful）都抛这一码，业务侧无需再各自转译。
 */
public enum CommonResCode implements DomainResCode {

    /** 并发冲突：资源已被他人修改（乐观锁 CAS 未命中 / body {@code lockedVersion} 不一致）→ 409。 */
    VERSION_CONFLICT(DomainErrorType.CONFLICT, 300010024, "资源 %s 已变化（期望版本 %s），请刷新后重试"),
    /** 数据约束冲突（唯一键 / 外键等）→ 409。 */
    CONSTRAINT_CONFLICT(DomainErrorType.CONFLICT, 300010025, "资源 %s 违反数据约束（唯一性或引用关系）");

    private final DomainErrorType type;
    private final int code;
    private final String desc;

    CommonResCode(DomainErrorType type, int code, String desc) {
        this.type = type;
        this.code = code;
        this.desc = desc;
    }

    @Override
    public DomainErrorType getType() {
        return type;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
