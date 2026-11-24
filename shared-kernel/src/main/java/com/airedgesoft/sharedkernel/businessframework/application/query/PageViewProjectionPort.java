package com.airedgesoft.sharedkernel.businessframework.application.query;

/**
 * 页面视图投影端口（只读计算投影；由 bridge 读 v3 分类配置 + 映射配置装配）。
 *
 * <p>
 * 存在原因：shared-kernel 不得直连 v3 元数据；控制器只依赖本端口，v3 直读实现落在 bridge-v3。
 */
public interface PageViewProjectionPort {

    /**
     * 解析页面视图（字段信息 + 按钮信息）。
     *
     * @param query
     *            解析条件（分类 / 用途 / 页签 / 表）
     * @return 页面视图投影；无字段/按钮时对应数组为空
     */
    QPageView resolve(ResolvePageViewQuery query);
}
