package com.airedgesoft.lib.restful.etag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点的 <b>并发身份声明</b>（规范 §6.7）：声明本端点响应体里的 {@code lockedVersion} 代表<b>哪个资源</b>的并发代数。
 *
 * <p>
 * <b>与 ETag 无关</b>：{@code ETag} 恒为响应表示的 hash（框架自动计算），本注解不参与 ETag 生成，只决定 {@code lockedVersion} 绑定的资源身份。
 *
 * <p>
 * <b>为什么必须显式声明</b>：并发代数属于某个资源，其身份决定该端点读写的 {@code lockedVersion} 是谁的—— 单资源端点 = 自身；聚合内子资源写端点 = <b>所属聚合</b>（子实体无独立版本）；批量端点
 * = 集合条目各自的身份。 身份若靠运行时推断（载荷 {@code name} 末段 / URL 片段），在「聚合内嵌套」与「兄弟聚合嵌套」两种形态下会得出不同答案 （前者需父聚合、后者需自身），无法由通用规则区分——故由端点显式声明。
 *
 * <p>
 * <b>取值语义</b>：
 * <ul>
 * <li>{@code @EtagResource("taskId")}：身份 = 路径变量 {@code taskId} 的运行时值（子资源写端点指向<b>父聚合</b>）；</li>
 * <li>{@code @EtagResource}（无值）：身份 = 载荷自身（AIP-122 {@code name} 末段，如创建响应——id 仅落库后可知）。</li>
 * </ul>
 *
 * <p>
 * <b>附带语义</b>：本端点的写请求在 body 携带 {@code lockedVersion}，期望版本绑定到同一身份（基础设施在加载该资源时比对，不一致 → 409）。
 * 因此单资源写端点应<b>始终</b>标注本注解，使「比较谁」在全链路上唯一。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EtagResource {

    /**
     * 身份来源的路径变量名（留空 = 载荷自身）。
     */
    String value() default "";
}
