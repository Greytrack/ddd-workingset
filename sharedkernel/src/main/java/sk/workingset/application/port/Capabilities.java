package sk.workingset.application.port;

/**
 * 业务对象类型参与 Working Set 协作的能力声明（T4 §3.4）。
 *
 * <p>能力不支持不等于空结果；调用方必须按 {@link sk.workingset.application.FailureReason#CAPABILITY_NOT_SUPPORTED}
 * 处理，而不是静默跳过。
 */

/**
 * 业务对象类型参与 Working Set 协作的能力声明（T4 §3.4 的当前实现子集）。
 *
 * <p>当前只使用 {@code comparable} 与 {@code derivable}；其余能力待对应路径接入时再声明，
 * 避免空实现和未使用字段膨胀。
 */
public record Capabilities(boolean comparable,
                           boolean derivable) {
}
