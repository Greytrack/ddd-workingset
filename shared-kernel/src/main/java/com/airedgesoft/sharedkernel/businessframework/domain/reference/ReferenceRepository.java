package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect.PropagationSuspectHolderRepository;

import java.util.Collection;
import java.util.List;

/**
 * 聚合仓储端口（⛁）：引用行读写；继承传播怀疑父仓储（触发口径 8——引用行经父仓储进入公共标记／清标记链路）。
 *
 * <p>
 * 历史版次求值在仓储侧完成（口径 11）：{@code findEffectiveRows} 家族按「{@code revision ≤ N} 的最后一条记录」判定有效性， 调用方不得只看行是否存在（五-6）。
 */
public interface ReferenceRepository extends PropagationSuspectHolderRepository<Reference> {

    /**
     * 批量保存行（聚合整体写入：行 ＋ 版次记录同事务；六-6「先建行后补记录」禁止）。
     *
     * @param rows
     *            待保存的引用聚合集合
     */
    @Override
    void save(List<Reference> rows);

    /**
     * 整行删除（记录集只剩失效记录的行，口径 3）。
     *
     * @param referenceIds
     *            待删除的引用 id 集合
     */
    void remove(List<Long> referenceIds);

    /**
     * 按身份批量加载（重建完整聚合：行 ＋ 记录集 ＋ 怀疑状态）。
     *
     * @param ids
     *            引用 id 集合；未命中缺席
     * @return 命中的聚合集合
     */
    List<Reference> findByIds(Collection<Long> ids);

    /**
     * 引用方在指定属性下、指定版次的有效引用行（历史求值在读侧，口径 11）。
     *
     * @param sourceId
     *            引用方需求 id
     * @param attributeApiId
     *            引用属性 apiId
     * @param revision
     *            引用方版次号
     * @return 有效行集合
     */
    List<Reference> findEffectiveRows(Long sourceId, String attributeApiId, int revision);

    /**
     * 引用方整体的有效引用行（不分属性——签出打记录按引用方整体取，12 号稿 §四）。
     *
     * @param sourceId
     *            引用方需求 id
     * @param revision
     *            引用方版次号
     * @return 有效行集合
     */
    List<Reference> findEffectiveBySource(Long sourceId, int revision);
}
