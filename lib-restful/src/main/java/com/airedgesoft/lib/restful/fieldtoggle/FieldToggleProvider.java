package com.airedgesoft.lib.restful.fieldtoggle;

import java.util.Set;

/**
 * 预定义字段开关 SPI（遵循矩阵「预定义字段开关（Field Availability Toggles）」行，Tier B 声明式接管）。
 *
 * <p>
 * <b>分工</b>：可选预定义字段是否提供属 <b>BC 部署期配置</b>（配置中心按 BC 承载启停），框架只收口执行——BC 实现<b>本接口并注册为 Bean</b>， 声明「按集合（URI collection
 * 段）的当前停用字段集合」；lib 侧 {@link FieldToggleWriteAspect}（写路径 updateMask 引用停用字段 → 400）与
 * {@code readmask.ReadMaskAspect}（读路径停用字段并入剪枝通道，读投影恒不含）自动生效。
 *
 * <p>
 * <b>语义（字段不存在）</b>：停用即「该字段对当前部署不存在」——读投影剔除、读写引用判 400；不提供运行时动态扩展字段（§15 万能属性 Map 禁令）。
 *
 * <p>
 * <b>集合键</b>：{@code collection} 为 URI 集合段（{@code /v1/demoTasks/101} → {@code demoTasks}；集合级冒号动词
 * {@code /v1/demoTasks:batchUpdate} → {@code demoTasks}），派生口径见
 * {@link com.airedgesoft.lib.restful.path.RequestCollections#of}—— BC 声明注册表与框架裁决共用同一派生口径，不出现两把 key。
 *
 * <p>
 * <b>零开销缺省</b>：无同型 Bean 时整组能力静默不激活（读写路径原样放行，无反射、无查找开销）。停用集合须<b>不可变</b>（{@code Set.copyOf} 等）， 框架在请求路径上直接读取。
 */
public interface FieldToggleProvider {

    /**
     * 指定资源集合当前停用的可选预定义字段集合（无停用 → 空集；禁止返回 null）。
     *
     * @param collection
     *            URI 集合段（{@link com.airedgesoft.lib.restful.path.RequestCollections#of} 派生；无法定位 → null，实现应返回空集）
     */
    Set<String> disabledFields(String collection);
}
