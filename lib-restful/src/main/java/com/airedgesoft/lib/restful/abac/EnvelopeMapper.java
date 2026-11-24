package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.pagination.Page;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 响应信封的<b>逐条 copy-on-change 映射</b>（{@code Page} / {@code Collection} / {@code Etagged} 信封条目）—— readMask 投影
 * ({@code readmask.ReadMaskAspect}) 与 ABAC 脱敏（{@code IncludeAccessAspect}）/ 流式导出共用的唯一信封遍历实现， 两条通道只各提供一个<b>资源级变换函数</b>
 * （置 null 剪枝 / 无权字段脱敏），信封形态分派与重建不再各写一份。
 *
 * <p>
 * <b>共同语义</b>（两通道等价前提）：变换无变化返回<b>原引用</b>（copy-on-change，免无谓重建）；有条目变化才重建信封—— {@link Page} 保留 {@code nextPageToken} /
 * {@code totalSize}，{@link Etagged} 保留 {@code lockedVersion}；集合稀疏位不补； 非 {@code List} 集合按迭代序重建为 {@code List}（保序）。条目级
 * {@link Etagged} 信封解开变换后重建（保留并发代数），非信封条目直接变换。
 *
 * <p>
 * <b>边界</b>：本类只处理条目级信封（{@link Page} / {@code Collection} 的元素与单个 {@link Etagged}）； 顶层形态分派（{@code Optional} /
 * {@code ResponseEntity} / 单资源判定）属各调用方的通道差异，不在本类。
 */
public final class EnvelopeMapper {

    private EnvelopeMapper() {
    }

    /** Page 条目逐条变换：有条目变化才重建信封（游标 / 总数原样保留），无变化返回原 Page。 */
    @SuppressWarnings("unchecked")
    public static <T> Page<T> mapPage(Page<T> page, UnaryOperator<Object> resourceTransform) {
        List<T> items = page.items();
        List<T> mapped = null;
        for (int i = 0; i < items.size(); i++) {
            T original = items.get(i);
            T transformed = (T) mapItem(original, resourceTransform);
            if (transformed != original && mapped == null) {
                mapped = new ArrayList<>(items);
            }
            if (mapped != null) {
                mapped.set(i, transformed);
            }
        }
        return mapped == null ? page : Page.of(mapped, page.nextPageToken(), page.totalSize());
    }

    /** Collection 条目逐条变换（稀疏列表缺失位不补）；有条目变化才重建副本（保迭代序），无变化原样透传。 */
    @SuppressWarnings("unchecked")
    public static <T> Collection<T> mapCollection(Collection<T> collection, UnaryOperator<Object> resourceTransform) {
        List<T> mapped = null;
        int index = 0;
        for (T original : collection) {
            T transformed = (T) mapItem(original, resourceTransform);
            if (transformed != original && mapped == null) {
                mapped = new ArrayList<>(collection);
            }
            if (mapped != null) {
                mapped.set(index, transformed);
            }
            index++;
        }
        return mapped == null ? collection : mapped;
    }

    /**
     * 条目级变换：先解开 {@link Etagged} 信封变换载荷，变化时重建信封（保留并发代数）；非信封条目直接变换。 变换返回原引用则条目原样返回。
     */
    public static Object mapItem(Object item, UnaryOperator<Object> resourceTransform) {
        if (item instanceof Etagged<?> envelope) {
            Object transformed = resourceTransform.apply(envelope.resource());
            return transformed == envelope.resource() ? item : new Etagged<>(transformed, envelope.lockedVersion());
        }
        return resourceTransform.apply(item);
    }
}
