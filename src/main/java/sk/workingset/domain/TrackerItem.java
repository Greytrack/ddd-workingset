package sk.workingset.domain;

/**
 * 公共领域契约（D-007）：业务聚合实现它即可获得工作集公共能力。
 * <p>只表达公共身份与业务版次；不含应用层调用、授权或写入方法（T4 §3.1）。
 */
public interface TrackerItem {

    LocalId localId();

    CommonId commonId();

    Revision revision();
}
