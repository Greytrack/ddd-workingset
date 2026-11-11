package sk.workingset.application.port;

import sk.workingset.application.Caller;
import sk.workingset.application.CapabilitySet;
import sk.workingset.application.Operation;
import sk.workingset.application.TrackerItemContent;
import sk.workingset.domain.Revision;

/**
 * 只读公共契约（T4 §3.1）：由业务 BC 的 Adapter 实现；SK 只依赖该接口。
 *
 * <p>该接口按「一个变体」实例化（构造时绑定 {@code LocalId}），不提供写方法、整体校验或提交。
 */
public interface TrackerItemReadPort {

    /** 按业务版次读取完整内容（BC 内部重建）；版次不可读时须显式失败，不得用当前值冒充。 */
    TrackerItemContent readAt(Revision revision);

    /** 当前业务版次。 */
    Revision revision();

    /** 能力声明。 */
    CapabilitySet capabilities();

    /** 授权事实（BC 或 Privilege 提供）；S1 恒允许。 */
    boolean authorize(Operation operation, Caller caller);
}
