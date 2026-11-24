package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.PlacementService;

import java.util.List;

/**
 * 仓储端口（SPI · 跨域通用）：可排序树节点的容器成员与树结构读取。
 *
 * <p>
 * 与 {@link OrderTreeable} 配套：容器成员直接返回<b>节点自身</b>（{@link OrderTreeable} 领域对象，身份 {@link OrderTreeable#getId()}、位置值
 * {@link OrderTreeable#getOrderIndex()}），供 {@link PlacementService} / {@link OrderPolicy}
 * 落位算值；树结构事实（子树、子节点、祖先链、高度）供成环判定与层级裁决。不透露任何持久化机制。
 *
 * <p>
 * <b>只读契约</b>：只声明读方法，无事务、无事件、无副作用。
 *
 * <p>
 * <b>含已删除</b>：容器成员与树结构（子树／子节点／祖先链／高度）**均返回已删除对象**（墓碑仍占位，避免位置值复用）； 展示层过滤由调用方决定。
 *
 * <p>
 * <b>深度口径</b>：深度类结果按「含起点自身」计；需要「不限深」时传业务上限（如 {@code Integer.MAX_VALUE}）， 接口不提供无限语义的重载。
 *
 * <p>
 * <b>失败语义</b>：入参为空或对象不存在 → 空集／空链／0，<b>不抛</b> NOT_FOUND；深度类结果按「含起点自身」计数。
 *
 * <p>
 * <b>框架纪律</b>：本接口属 shared-kernel 领域层「业务对象」包，包内零框架技术依赖——<b>不挂</b> Spring / ORM 注解； 实现由各 BC 的
 * {@code infrastructure.repositories} 提供（如需求仓储 {@code RequirementRepository} 继承本端口），底层表级 SQL 由各 BC 的 {@code *Mapper}
 * 承担（表名以<b>动态入参</b>传入，不硬编码具体 BC 的表）。
 */
public interface OrderTreeableRepository {

    /**
     * 同父兄弟全集（{@code parentId} 相同；含已删除——拖拽等场景需全量）。
     *
     * @param parentId
     *            父对象 id；为空返回空集
     * @return 容器成员（节点自身，位置值经 {@link OrderTreeable#getOrderIndex()} 读）
     */
    List<OrderTreeable> findByParentId(Long parentId);

    /**
     * 根层容器成员（父边为空，按根层分桶键分桶；需求域分桶键＝跟踪分类 trackerId）。
     *
     * @param rootBucketId
     *            根层分桶键；为空返回空集
     * @return 容器成员（节点自身，位置值经 {@link OrderTreeable#getOrderIndex()} 读）
     */
    List<OrderTreeable> findRootMembers(Long rootBucketId);

    /**
     * 子树 id（层序，父先于子，含起点自身）。
     *
     * <p>
     * 一次取回、不递归调用；{@code maxDepth} 相对 {@code objectId} 计（自身＝1），取到第 {@code maxDepth} 层即截断。
     *
     * @param objectId
     *            子树根 id；为空返回空集
     * @param maxDepth
     *            最大层数（含自身）；非正数返回空集
     * @return 层序 id 集合
     */
    List<Long> findSubtree(Long objectId, int maxDepth);

    /**
     * 一层子节点 id（不含起点自身）。
     *
     * @param objectId
     *            父对象 id；为空返回空集
     * @return 直接子节点 id 集合
     */
    List<Long> findChildIds(Long objectId);

    /**
     * 祖先链 id（自身起、向上到根，含起点自身）。
     *
     * <p>
     * 用途：落位成环判定（目标父链上出现移动者即拒）。
     *
     * @param objectId
     *            起点 id；为空返回空集
     * @return 祖先链 id 集合（自身在前）
     */
    List<Long> findAncestors(Long objectId);

    /**
     * 子树高度：{@code objectId} 以下的最大层数（自身＝1，无子＝1）。
     *
     * @param objectId
     *            起点 id；为空或不存在返回 0
     * @return 层数
     */
    int findSubtreeHeight(Long objectId);
}
