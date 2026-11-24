package com.airedgesoft.lib.restful.singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记：单例资源 Controller（AIP-156 单例资源；v4-restful-api.md §5 单例子资源 / 合规矩阵「单例资源」行）。
 *
 * <p>
 * <b>单例资源</b> = 无集合语义、无 ID 段的全局唯一配置型端点（如 {@code GET /v1/settings} / {@code PATCH /v1/settings}）——资源即实例，URI 恒为
 * {@code /v1/{name}}，不存在「集合 → 条目」两级寻址，也不存在条目级 {@code /v1/{res}/{id}} 形态。与之相对，集合资源（AIP-121）的写入口为 {@code POST
 * /v1/{res}}（创建）与 {@code POST /v1/{res}/{id}:{verb}}（命令），二者 URI 形态相同、语义相反，框架无法从路由本身区分——故以显式注解声明意图。
 *
 * <p>
 * <b>方法约束</b>：标注本注解的 Controller 仅允许 <b>GET</b>（读取单例表示）与 <b>PATCH</b>（部分更新，AIP-161 {@code updateMask} 口径）； <b>禁止 POST 与
 * DELETE</b>——单例无「创建」（资源恒存在）亦无「删除」（配置项不可删除，只能更新）语义，集合级冒号动词 {@code :batchCreate} / {@code :batchDelete} 等随之不适用。 与 §5
 * 嵌套单例子资源（{@code /v1/{res}/{id}/settings}，含 DELETE = 重置语义）不同：顶层单例是资源本身，「重置」即 PATCH 回默认值，不存在删除端点。
 *
 * <p>
 * <b>门禁语义（供 ArchUnit 规则扫描识别）</b>：本注解为<b>显式契约</b>——<b>只有标注了本注解的 Controller</b> 才受上述单例方法约束； 未标注的 Controller 即使路径形态形似单例（如
 * {@code /v1/{name}}）也不受拦截，避免把集合级 {@code :{verb}} 命令 POST（如 {@code POST /v1/demoTasks/1:activate}）误伤为单例违规。 ArchUnit
 * 规则由宿主 / demo 模块落地（本注解仅承载契约，无切面无运行时逻辑）。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SingletonResource {
}
