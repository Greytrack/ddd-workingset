package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 评论读模型（评论为顶级资源，{@code target} 为 AIP-122 资源名）。
 *
 * @param name
 *            AIP-122 资源名（相对形态，如 comments/623809242866400）
 * @param id
 *            评论 id（十进制字符串）
 * @param target
 *            目标对象资源名（如 requirements/623809242866016）
 * @param content
 *            评论内容
 * @param replyTo
 *            被回复评论资源名（comments/{id}；可空 = 顶级评论）
 * @param authorActorId
 *            作者 id
 * @param authorDisplayName
 *            作者只读展示名（§16）
 * @param createTime
 *            发表时刻（RFC 3339，output-only）
 * @param updateTime
 *            最近修订时刻（RFC 3339，output-only）
 */
@Schema(description = "评论资源")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QComment(@Schema(description = "AIP-122 资源名（相对形态）") String name,
        @Schema(description = "评论 id（十进制字符串）") String id,
        @Schema(description = "目标对象资源名", example = "requirements/623809242866016") String target,
        @Schema(description = "评论内容") String content,
        @Schema(description = "被回复评论资源名（comments/{id}；可空）") String replyTo,
        @Schema(description = "作者 id") String authorActorId, @Schema(description = "作者只读展示名") String authorDisplayName,
        @Schema(description = "发表时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String createTime,
        @Schema(description = "最近修订时刻（RFC 3339）", accessMode = Schema.AccessMode.READ_ONLY) String updateTime) {
}
