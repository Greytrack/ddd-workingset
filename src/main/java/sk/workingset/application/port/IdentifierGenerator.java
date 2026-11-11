package sk.workingset.application.port;

/**
 * 标识生成器（P1）：业务标识使用雪花 ID，与 FACT-005「CommonId/LocalId 为雪花 ID」一致。
 *
 * <p>SK 应用层只依赖该接口；具体实现（雪花算法）由组合根装配。
 */
public interface IdentifierGenerator {

    /** 生成下一个全局唯一标识（单调递增趋势，不保证连续）。 */
    long nextId();
}
