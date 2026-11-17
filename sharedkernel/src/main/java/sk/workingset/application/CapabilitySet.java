package sk.workingset.application;

/**
 * 对象/BC 的能力声明（T4 §3.4）。不支持的能力必须显式失败，不得返回空结果。
 */
public record CapabilitySet(boolean comparable,
                            boolean mergeable,
                            boolean derivable,
                            boolean creatable,
                            boolean historyReadable) {

    /** 首切片 ALM Requirement：可比较、可合并、可派生、可创建；历史回读暂不实现。 */
    public static CapabilitySet full() {
        return new CapabilitySet(true, true, true, true, false);
    }

    /** Part/DQA 类共享查看对象：不参与派生/比对/合并。 */
    public static CapabilitySet readOnly() {
        return new CapabilitySet(false, false, false, false, true);
    }
}
