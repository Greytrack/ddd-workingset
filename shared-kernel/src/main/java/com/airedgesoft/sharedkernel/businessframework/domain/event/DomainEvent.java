package com.airedgesoft.sharedkernel.businessframework.domain.event;

/**
 * 领域事件标记接口。
 *
 * <p>
 * 对应 14 需求建模契约 {@code <<interface DomainEvent>>}：聚合在行为完成时<b>产出</b>事件，应用服务在<b>事务提交后</b>发布 （Outbox / 发布器随实现轮落地）。 产出方式由各
 * BC 自持：既可由聚合行为<b>以返回值交出</b>（如需求聚合 {@code markCreated} / {@code modifyFields} / {@code delete}），
 * 也可由聚合内记录后统一取用——无论哪种，事件都与业务变更同事务、不丢失。
 *
 * <p>
 * 各 BC 的具体事件（如 {@code RequirementCreated} / {@code RequirementModified} / {@code RequirementDeleted}） 实现本接口；
 * 事件载荷（occurredOn / 版本等通用头）随发布机制实现轮统一补，本轮只承载语义标记。
 */
public interface DomainEvent {
}
