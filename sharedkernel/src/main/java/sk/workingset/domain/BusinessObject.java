package sk.workingset.domain;

/**
 * 所有业务对象共有的生命周期能力声明（D1/D14，用户 2026-11-12 裁定）。
 *
 * <p>能力公开、所有 BC 的所有 Object 都具备；具体删除规则、guard 与编排由各业务 BC 实现。
 * SK 不通过本接口写业务表，也不提供通用删除实现。
 */
public interface BusinessObject {

    LocalId localId();

    CommonId commonId();

    /** 软删：保留业务数据但从正常读取隐藏。 */
    LifecycleResult delete(LifecycleCommand command);

    /** 恢复软删。 */
    LifecycleResult undelete(LifecycleCommand command);

    /**
     * 永久删除的 guard/decision；实际永久删除由所属 BC 的应用服务编排。
     *
     * <p>返回 {@link LifecycleResult.Status#Applied} 表示允许继续；对象本身不在此处被移除。
     */
    LifecycleResult delete2(LifecycleCommand command);
}
