package com.airedgesoft.lib.restful.etag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

/**
 * 并发代数<b>协议适配器</b>（规范 §7.2）：信封<b>显式携带</b> {@code lockedVersion}（对外并发代数）， 把无版本概念的纯数据载荷（application 层 {@code Q*}）适配为
 * {@link Etag}，供 {@link EtagResponseAdvice} 以单一意图完成 HTTP 出口装配（并发代数注入、201 → Location）。
 *
 * <p>
 * <b>分层归属（RESTful 协议内容）</b>：本类型<b>仅供 adapters（Controller 层）装配</b>。并发代数的来源是 {@link EtagContext}（仓储加载 /
 * 保存时观测回写的请求级上下文）——application 层与领域模型<b>不出现任何版本概念</b>， {@code Q*} 保持纯数据 record（不再声明 {@code lockedVersion} 字段）。
 *
 * <p>
 * wire 形态 = 载荷字段<b>平铺</b>（{@link JsonUnwrapped}）+ 信封 {@code lockedVersion} 恰好出现一次
 * （{@link JsonInclude#NON_NULL}：无版本记录的条目省略该字段）。单资源与集合条目同名同义——集合端点由框架逐条包装并注入 {@code lockedVersion}。 版本只走
 * body；{@code ETag} 恒为响应表示 hash（由 {@link EtagResponseAdvice} 计算），不再下发逐条令牌响应头（规范 §6.6 / §7.1.3）。
 *
 * <p>
 * <b>命名裁决（规范 §2 家族）</b>：并发协议类型以 {@code Etag} 命名；<b>禁止以裸 {@code Versioned} 命名</b>—— PLM 业务中「版次 /
 * Versioned」是重要业务概念，该名不再回收。<b>历史命名说明</b>：本类型<b>不参与 ETag 计算</b>，{@code ETag} 恒为响应表示 hash、与并发无关；类名沿用历史， 避免破坏既有引用。
 *
 * @param <T>
 *            资源载荷类型（application 层 {@code Q*}，纯数据、无版本字段）
 * @param resource
 *            资源载荷（序列化时平铺到顶层；禁止为 null）
 * @param lockedVersion
 *            对外并发代数（≥1 = 存储 {@code lock_version} + 1；通常经 {@link EtagContext#currentGeneration} 取得； null 时 wire
 *            省略该字段，不影响 ETag 的表示 hash 语义）
 */
@Schema(description = "携带并发代数的资源载荷（协议适配器；字段平铺，lockedVersion 由信封承载）")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Etagged<T>(
        @Schema(description = "资源载荷（wire 平铺；OpenAPI 由 EtagSchemaCustomizer 展开）") @JsonUnwrapped T resource,
        @Schema(description = "对外并发代数（从 1 起算；单资源与列表条目同名同义）") Long lockedVersion) implements Etag {

    public Etagged {
        Objects.requireNonNull(resource, "Etagged.resource 不能为空（无载荷端点应返回 void，规范 §9 第 15 条②）");
    }

    /** 显式并发代数装配（值来自 {@link EtagContext#currentGeneration}；框架内部使用）。 */
    public static <T> Etagged<T> of(T resource, Long lockedVersion) {
        return new Etagged<>(resource, lockedVersion);
    }

    /** 按资源相对名（{@code collection/id}）从 {@link EtagContext} 取当前代数装配（无记录 → null；框架内部使用）。 */
    public static <T> Etagged<T> from(T resource, String resourceName) {
        return new Etagged<>(resource, EtagContext.currentGeneration(resourceName));
    }
}
