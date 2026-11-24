package com.airedgesoft.sharedkernel.businessframework.application.query;

/**
 * 页面视图解析查询（只读计算投影；任务书 §2.1，2026-11-20）。
 *
 * @param classificationId
 *            分类 id（必填）
 * @param purpose
 *            用途（必填：DETAIL/LIST/EDIT/CREATE）
 * @param tabKey
 *            页签 id（LIST 必填；取值即 V3 列表 id——用户裁决 2026-11-20「tabKey 即 V3 id」，不做稳定键映射；服务端换算列表按钮与回显 {@code tab}）
 * @param tableKey
 *            表槽位稳定键（LIST 同一页签多表时用；回显，服务端不消费）
 * @param variant
 *            新建形态（CREATE 时：MODAL 默认 / WINDOW；非 CREATE 忽略）
 */
public record ResolvePageViewQuery(String classificationId, QPageView.Purpose purpose, String tabKey, String tableKey,
        QPageView.Variant variant) {
}
