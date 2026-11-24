package com.airedgesoft.lib.restful.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 通用集合分页信封（规范 §7.1 协议信封白名单成员——lib-restful 统一维护的通用泛型信封之一，与 {@code Etagged<T>} 同列）。
 *
 * <p>
 * 规避为每个资源重复定义同构的 {@code {Noun}Page} 样板类。
 *
 * @param <T>
 *            集合元素类型（application 层 {@code Q*} 纯数据；出口由 advice 逐条包装为 {@code Etagged} 注入 {@code lockedVersion}，ETag 为整页表示的
 *            hash，规范 §6.6）
 * @param items
 *            当前页资源列表
 * @param nextPageToken
 *            下一页游标（AIP-158 不透明加密字符串；末页为 null，空串归一化为 null）
 * @param totalSize
 *            过滤后匹配总数（可选，按端点场景决定是否下发）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "通用集合分页信封（AIP-158 游标分页）")
public record Page<T>(@Schema(description = "当前页数据列表") List<T> items,
        @Schema(description = "下一页游标（AIP-158 不透明字符串；末页为 null，空串归一化为 null）") String nextPageToken,
        @Schema(description = "过滤后匹配总数") Integer totalSize) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
        nextPageToken = nextPageToken == null || nextPageToken.isBlank() ? null : nextPageToken;
    }

    public static <T> Page<T> of(List<T> items, String nextPageToken, Integer totalSize) {
        return new Page<>(items, nextPageToken, totalSize);
    }
}
