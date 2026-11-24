package com.airedgesoft.sharedkernel.businessframework.domain.association;

import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspectHolderRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 聚合仓储端口（⛁）：关联行读写；继承传播怀疑父仓储（触发口径 8——关联行经父仓储进入公共标记／清标记链路）。
 *
 * <p>
 * 「一个人为一行」：一次增删改 N 行＝N 个聚合的多实例操作，仓储按行集合读写（约束五-1）。
 */
public interface AssociationRepository extends PropagationSuspectHolderRepository<Association> {

    /**
     * 批量保存行（聚合整体写入；新增/修改同一条链路）。
     *
     * @param rows
     *            待保存的关联行集合
     */
    @Override
    void save(List<Association> rows);

    /**
     * 批量删除行（只删关系行与规则底座，两端对象不动）。
     *
     * @param associationIds
     *            待删除的关联行 id 集合
     */
    void remove(List<Long> associationIds);

    /**
     * 按身份批量加载（重建完整聚合：两端、类型、怀疑状态、规则、可选属性）。
     *
     * @param ids
     *            关联行 id 集合；未命中缺席
     * @return 命中的聚合集合
     */
    List<Association> findByIds(Collection<Long> ids);

    /**
     * 按身份加载单行。
     *
     * @param associationId
     *            关联行 id
     * @return 命中返回聚合，缺失返回空
     */
    Optional<Association> findById(Long associationId);
}
