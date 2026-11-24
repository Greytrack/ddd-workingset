package com.airedgesoft.sharedkernel.businessframework.application;

/**
 * 当前操作人提供端口（迁移期）。
 *
 * <p>
 * v4 的鉴权/身份归属 {@code v4-identityaccess}（尚未实现 http 上下文桥接），迁移期由 {@code bridge-v3} 复用 legacy 请求上下文提供操作人。工作流 BC 只依赖本端口，不直接读
 * legacy ThreadLocal。
 */
public interface CurrentOperatorProvider {

    /**
     * 当前请求的操作人用户 id。
     *
     * @return 用户 id
     */
    long currentOperatorId();

    /** 同步适配器可沿用当前身份，后台接入方可恢复指定操作人的会话。 */
    default Scope openScope(long operatorId) {
        if (currentOperatorId() != operatorId) {
            throw new IllegalStateException("当前适配器不支持切换操作人");
        }
        return Scope.UNCHANGED;
    }

    /** 在执行结束时恢复调用线程原有的操作人。 */
    interface Scope extends AutoCloseable {
        Scope UNCHANGED = () -> { };

        @Override
        void close();
    }
}
