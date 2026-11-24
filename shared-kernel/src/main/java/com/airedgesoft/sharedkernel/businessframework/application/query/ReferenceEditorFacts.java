package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceGuardDomainService;

/**
 * 引用方编辑事实端口（应用读端口 · 依赖倒置回注点）。
 *
 * <p>
 * 引用方的「版次 / 签出态 / 删除位」语义属于<b>源对象 BC 的私有聚合</b>（需求、物料等各有其聚合与仓储）， 引用公共域只认 {@link ReferenceGuardDomainService.EditorFacts}
 * 这份事实。各消费方 BC 实现本端口（如 v4-alm 从 {@code RequirementRepository} 读需求聚合），shared-kernel 不反向依赖任何 BC。
 */
public interface ReferenceEditorFacts {

    /**
     * 取引用方编辑事实。
     *
     * @param sourceId
     *            引用方对象 id
     * @return 编辑事实
     */
    ReferenceGuardDomainService.EditorFacts loadEditorFacts(Long sourceId);
}
