package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 小规模集合（子资源 / 只读投影）的<b>内存分页</b>（AIP-158:173-176：分页字段必须真实实现且默认值非无限；小集合允许「取全量再切片」的内存版）。
 *
 * <p>
 * 与 {@link PageTokens} 互补：后者面向大集合的数据库主键 seek（AES-GCM 不透明游标 + filter 指纹）；本工具面向聚合内有界集合 （步骤 / 版次 / 链接等），游标为 Base64URL 编码的
 * offset，篡改 / 非法 → 400 {@link DomainRestCode#PAGE_TOKEN_INVALID}。
 *
 * <p>
 * pageSize 语义与 {@link PageSizeResolver} 一致：缺省 / 0 → 默认；负值 → 400 {@link DomainRestCode#PAGE_SIZE_INVALID}；超上限钳到上限。
 */
public final class InMemoryPages {

    /** 子资源集合默认页大小（非无限，AIP-158:174）。 */
    public static final int DEFAULT_PAGE_SIZE = 50;

    /** 子资源集合页大小上限。 */
    public static final int MAX_PAGE_SIZE = 200;

    private InMemoryPages() {
    }

    /** 用默认页大小 / 上限对全量列表切片，返回 Page 信封（totalSize = 全量条数，nextPageToken 末页为 null）。 */
    public static <T> Page<T> paginate(List<T> all, Integer pageSize, String pageToken) {
        return paginate(all, pageSize, pageToken, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    /** 指定默认 / 上限的内存分页。 */
    public static <T> Page<T> paginate(List<T> all, Integer pageSize, String pageToken, int defaultSize, int maxSize) {
        int size = resolveSize(pageSize, defaultSize, maxSize);
        int offset = parseOffset(pageToken);
        List<T> items = all == null ? List.of() : all;
        int total = items.size();
        int from = Math.min(offset, total);
        int to = Math.min(from + size, total);
        String next = to < total ? encodeOffset(to) : null;
        return new Page<>(items.subList(from, to), next, total);
    }

    private static int resolveSize(Integer pageSize, int defaultSize, int maxSize) {
        int ceiling = Math.max(maxSize, defaultSize);
        if (pageSize == null || pageSize == 0) {
            return Math.min(defaultSize, ceiling);
        }
        if (pageSize < 0) {
            throw new DomainException(DomainRestCode.PAGE_SIZE_INVALID, pageSize);
        }
        return Math.min(pageSize, ceiling);
    }

    private static int parseOffset(String token) {
        if (token == null || token.isBlank()) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8));
            if (offset < 0) {
                throw new IllegalArgumentException("negative offset");
            }
            return offset;
        } catch (IllegalArgumentException exception) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID, token);
        }
    }

    private static String encodeOffset(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }
}