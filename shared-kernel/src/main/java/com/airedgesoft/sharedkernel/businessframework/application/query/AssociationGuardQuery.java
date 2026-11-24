package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.domain.association.AssociationGuardDomainService;

import java.util.Collection;
import java.util.List;

/**
 * 关联守卫事实读端口（应用读端口：仅取事实不判定，SKILL 6.5-20）。
 *
 * <p>
 * 由关联写用例（{@code AssociationCommandService}）装配调用：端对象事实与既有下游行分别从 {@code LiteBusinessObjectRepository} 与关联现表取数；
 * 领域侧守卫（{@link AssociationGuardDomainService}）无 IO。
 *
 * <p>
 * <b>归属</b>：shared-kernel 公共域——关联行跨业务对象/业务线，守卫事实读口径各 BC 一致。
 */
public interface AssociationGuardQuery {

    /**
     * 端对象事实（存在 / 删除位 / 分类）。
     *
     * @param objectId
     *            对象 id
     * @return 事实（不存在时 exists=false）
     */
    AssociationGuardDomainService.EndFacts loadEndFacts(Long objectId);

    /**
     * 批量端对象事实（一次 lite 快照查询；缺失 id 返回 exists=false）。
     *
     * objectIds 对象 id 集合 对象 id -> 事实
     */
    java.util.Map<Long, AssociationGuardDomainService.EndFacts> loadEndFactsBatch(Collection<Long> objectIds);

    /**
     * 既有关联行（判重池：同源 + 同目标，不含关联类型——约束五-2）。
     *
     * @param sourceId
     *            源对象 id
     * @param targetIds
     *            目标对象 id 集合
     * @return 命中的下游行（空集合法）
     */
    List<Association> loadDownstreamRows(Long sourceId, Collection<Long> targetIds);
}
