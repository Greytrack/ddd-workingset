package sk.workingset.domain;

/**
 * 公共领域契约（D-007 / D14）：业务聚合实现它即可获得工作集公共能力。
 * <p>继承 {@link BusinessObject} 获得公共身份与生命周期能力声明；本接口增加业务版次。
 * 不含应用层调用、授权或写入方法（T4 §3.1，写入协作仍走各 BC 的 WritePort）。
 */
public interface TrackerItem extends BusinessObject {

    Revision revision();
}
