package com.airedgesoft.lib.restful.batch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * {@code :batchGet} 稀疏对齐保序工具（v4-restful-api.md §4 批量行 / AIP-231 batchGet；Tier B「:batchGet 稀疏对齐」）。
 *
 * <p>
 * 语义：返回结果<b>按请求 ID 顺序对齐</b>；缺失 ID 静默跳过（稀疏返回——批量读不得整批 404），同时给出缺失清单供 207 信封 / 埋点使用。 请求 ID 去重保序（重复 ID 只取首个命中，防结果重复项）。
 *
 * @param found
 *            命中的资源，按请求顺序排列
 * @param missing
 *            请求了但未命中的 ID（请求顺序、已去重）
 * @param <T>
 *            资源载荷类型
 */
public record BatchAlignment<T>(List<T> found, List<String> missing) {

    /**
     * 把查询结果按请求 ID 顺序对齐。
     *
     * @param requestedIds
     *            请求 ID 序列（原样保序；内部去重）
     * @param foundResources
     *            存储层返回的资源（顺序不保证）
     * @param idExtractor
     *            资源 → 契约 ID
     */
    public static <T> BatchAlignment<T> align(List<String> requestedIds, List<T> foundResources,
            Function<T, String> idExtractor) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return new BatchAlignment<>(List.of(), List.of());
        }
        java.util.Map<String, T> byId = new java.util.LinkedHashMap<>();
        if (foundResources != null) {
            for (T resource : foundResources) {
                String id = idExtractor.apply(resource);
                byId.putIfAbsent(id, resource);
            }
        }
        LinkedHashSet<String> requested = new LinkedHashSet<>(requestedIds);
        List<T> found = new ArrayList<>(requested.size());
        List<String> missing = new ArrayList<>();
        for (String id : requested) {
            T resource = byId.get(id);
            if (resource != null) {
                found.add(resource);
            } else {
                missing.add(id);
            }
        }
        return new BatchAlignment<>(List.copyOf(found), List.copyOf(missing));
    }
}
