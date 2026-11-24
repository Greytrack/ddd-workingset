package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 分类视图<b>只读契约</b>（共享内核）：一次装配的分类元数据事实，供字段值装配与落库分桶读取。
 *
 * <p>
 * <b>不是模型</b>：本接口只有访问器——分类元数据的来源、投影（启用/引用过滤、主属性作用域、创建期默认值）与按 type 缓存在 bridge-v3（{@code ClassSnapshot} 实现本契约，装配方
 * {@code V3ClassificationModel}）。
 *
 * <p>
 * <b>寻址口径</b>：创建 / 修改契约以落库列名寻址（{@link #byColumn(String)}）；需要 apiId 寻址（如修改命令的 mask）时用 {@link #byApiId(String)}。两个索引指向同一批
 * {@link AttributeFacts}。
 *
 * <p>
 * <b>退出条件</b>：同 {@link AttributeFacts}——v4 分类元数据重建后随属性值校验族一并评估去留。
 */
public interface ClassificationFacts {

    /** 按落库列名取属性定义。 */
    Optional<AttributeFacts> byColumn(String column);

    /** 按 apiId 取属性定义。 */
    Optional<AttributeFacts> byApiId(String apiId);

    /** 落库列名 → 属性定义（供一次装配后整体传给领域层 / 构造器）。 */
    Map<String, AttributeFacts> byColumnValues();

    /** 主属性列集合（级联 / 继承属性的「主」列；装配期已解析）。 */
    Set<String> mainColumns();

    /** 子项页签是否启用（v3 子项页签 enabled 口径；创建期子项装配判据）。 */
    boolean subItemsTabEnabled();

    /** 全部可写属性定义（按列名索引顺序）。 */
    List<AttributeFacts> attributes();
}
