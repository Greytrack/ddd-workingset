package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 仓储端口（⛁ · 共享内核领域层 · 只读）：按身份取「业务对象只读快照」。
 *
 * <p>
 * 跨 BC 复用的对象事实读取接缝（IDDD ch7.3）——守卫与读侧拼装只需要「另一个聚合」的通用事实（编号、名称、分类、归属、版次摘要、删除位等）， 不需要装配对方聚合。快照形态见
 * {@link LiteBusinessObject}（{@code air_inst_common} 通用列投影）。
 *
 * <p>
 * <b>只读契约</b>：本接口只声明读方法，实现由各 BC {@code infrastructure.providers} 提供（读走 providers、写走 repositories）；
 * 调用方不得经由快照反推聚合状态，改动对方对象须走其自身应用服务。
 *
 * <p>
 * <b>失败语义</b>：缺失（或该身份在当前读面不可见）→ {@link Optional#empty()}／缺席，<b>不抛</b> NOT_FOUND；幂等、无副作用。 删除位口径照
 * {@code air_inst_common.delete_flag}（0＝未删除，空视为未删除），需要时由调用方自行判定 {@link LiteBusinessObject#isDeleted()}。
 *
 * <p>
 * <b>命名纪律</b>（2026-11-16 拍板）：方法面统一 {@code find} 家族——单条 {@link #findById}、批量 {@link #findByIds}； 不引入 {@code get*} /
 * {@code object*} 风格的同义方法。原 {@code LiteBusinessObjectService} 已收敛并入本接口。
 */
public interface LiteBusinessObjectProvider {

    /**
     * 按身份取只读快照。
     *
     * @param id 业务对象身份；为空返回空
     * @return 命中返回快照，缺失或不可见返回空
     */
    Optional<LiteBusinessObject> findById(Long id);

    /**
     * 按身份批量取只读快照。
     *
     * <p>
     * 缺失的 id 直接缺席（不补空位、不抛错），结果不做保序对应——调用方按 {@link LiteBusinessObject#id()} 自建索引。
     *
     * @param ids 业务对象身份集合；为空返回空集
     * @return 命中的快照集合（可能少于入参数量）
     */
    List<LiteBusinessObject> findByIds(Collection<Long> ids);
}
