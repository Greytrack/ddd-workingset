package com.airedgesoft.lib.restful.etag;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>条件读取（304）开关</b>（默认关闭，注解显式开启）。
 *
 * <p>
 * <b>ETag 头不受本注解影响</b>：任何端点（无论是否标注）都会下发表示 hash {@code ETag: "sha256-…"}——同一响应表示恒得同一 hash。 本注解只决定是否<b>消费</b>该 ETag
 * 做条件读取。
 *
 * <p>
 * <b>开启后的行为</b>：端点（或所在 Controller 类）标注本注解时，框架在写出表示后评估请求头 {@code If-None-Match}—— 命中则返回 <b>304</b>（不写 body），并下发
 * {@code Vary: Authorization, Accept, Accept-Encoding} 与 {@code Cache-Control: private, no-cache}，同时把表示 hash 写入
 * {@link EtagHashCache}。
 *
 * <p>
 * <b>未标注</b>：忽略 {@code If-None-Match}、恒 200 + 响应体；ETag 头照常下发（仍为表示 hash）。
 *
 * <p>
 * <b>何时开启</b>：读多写少、响应体较大且客户端会重复拉取的只读查询端点（如详情、树 / 图投影）。 需要按用户 / 掩码隔离的响应依赖 {@code Vary} 与授权上下文入 key，不受影响。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface EnableRepresentationCache {
}
