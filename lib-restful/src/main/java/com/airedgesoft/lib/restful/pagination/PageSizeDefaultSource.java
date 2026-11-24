package com.airedgesoft.lib.restful.pagination;

/**
 * 集合查询单页**默认条数**的来源端口（技术缝隙；lib-restful 分页策略的一部分，与 {@link PaginationProperties} 同为"分页治理"归属）。
 *
 * <p>
 * <b>为什么是端口</b>：默认条数在本系统并非技术常量——其**真实来源是 v3 智能规则「页面滚动加载数量」**（ {@code SmartRuleNode.scrollLoadSize}，可选 50/100/200/500，前端
 * {@code TableCommon} 用同一值做列表滚动加载）。 该值需经 v3 桥接读取，故此处只声明<b>技术缝隙</b>：缺省实现回落配置
 * {@link PaginationProperties#getDefaultPageSize()}， 由 <b>bridge-v3 的 v3 直读实现</b>（{@code V3PageSizeDefaults}）在宿主具备 v3
 * 时接管——各 BC 零改动即随系统配置生效。
 *
 * <p>
 * <b>契约</b>：返回 {@code null} 或 ≤ 0 = 「系统未配置」→ 调用方回落 {@link PaginationProperties#getDefaultPageSize()}； 返回值不参与硬上限钳制（上限见
 * {@link PaginationProperties#getMaxPageSize()}，调用方按 {@code max(上限, 默认值)} 取有效上限， 保证系统配置的滚动加载数量不被服务端上限截断）。
 */
@FunctionalInterface
public interface PageSizeDefaultSource {

    /** 系统配置的单页默认条数；{@code null} / ≤ 0 = 未配置（调用方回落配置默认值）。 */
    Integer defaultPageSize();
}
