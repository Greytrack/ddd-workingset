package com.airedgesoft.lib.restful.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 巨量数据流式导出（Tier D「巨量数据流式导入导出」基建；v4-restful-api.md 大结果集行——demo 端点见 {@code GET /v1/demoTasks:export}）。
 *
 * <p>
 * 基于 {@link StreamingResponseBody}（MVC 异步写出，不占用请求线程整段生成）+ <b>分批游标</b>：{@link PageFetcher} 按游标分批拉取
 * （每批一个查询，内存只持一批），{@link BatchConsumer} 逐批写出。内置 JSON Lines 消费器（{@link #jsonLines(ObjectMapper)}，每行一个 JSON
 * 对象——流式友好、消费方逐行解析）与小结果集可用的 JSON 数组消费器（{@link #jsonArray(ObjectMapper)} + {@link #jsonEpilogue}）。
 *
 * <p>
 * demo 接入形态（Controller 返回 {@code StreamingResponseBody}；导出端点建议 {@code GET /v1/{res}:export}，自定义方法语义）：
 *
 * <pre>
 * return StreamingExportSupport.stream(cursor -> repository.fetchPage(cursor, pageSize), // PageFetcher：cursor
 *                                                                                        // 入、Page(items, nextCursor) 出
 *         StreamingExportSupport.jsonLines(objectMapper));
 * </pre>
 */
public final class StreamingExportSupport {

    private StreamingExportSupport() {
    }

    /**
     * 带游标的一页数据。
     *
     * @param items
     *            本批条目（可为空 = 结束信号之一）
     * @param nextCursor
     *            下一批游标（null = 已到末尾）
     */
    public record Page<T>(List<T> items, String nextCursor) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }

        /** 是否还有后续批（items 非空且 nextCursor 非空——二者任一缺失视为末批）。 */
        boolean hasNext() {
            return !items.isEmpty() && nextCursor != null && !nextCursor.isBlank();
        }
    }

    /** 分批游标拉取器：cursor 入参为上页 {@link Page#nextCursor()}（首页传 null）。 */
    @FunctionalInterface
    public interface PageFetcher<T> {

        Page<T> fetch(String cursor);
    }

    /** 逐批消费写出（OutputStream 由 StreamingResponseBody 提供）。 */
    @FunctionalInterface
    public interface BatchConsumer<T> {

        void write(OutputStream out, List<T> batch) throws IOException;
    }

    /** 装配流式响应体：逐批拉取、逐批写出、逐批 flush；空批或无下一游标即结束。 */
    public static <T> StreamingResponseBody stream(PageFetcher<T> fetcher, BatchConsumer<T> consumer) {
        return out -> {
            String cursor = null;
            Page<T> page;
            do {
                page = fetcher.fetch(cursor);
                if (!page.items().isEmpty()) {
                    consumer.write(out, page.items());
                    out.flush();
                }
                cursor = page.nextCursor();
            } while (page.hasNext());
        };
    }

    /** NDJSON 消费器：每批逐行序列化（行 = 一个 JSON 对象 + \n）。 */
    public static <T> BatchConsumer<T> jsonLines(ObjectMapper objectMapper) {
        return (out, batch) -> {
            for (T item : batch) {
                out.write(objectMapper.writeValueAsBytes(item));
                out.write("\n".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    /** JSON 数组消费器（小结果集；跨批逗号由内部状态维护；结束信封见 {@link #jsonEpilogue}）。 */
    public static <T> BatchConsumer<T> jsonArray(ObjectMapper objectMapper) {
        return new BatchConsumer<>() {
            private boolean started;

            @Override
            public void write(OutputStream out, List<T> batch) throws IOException {
                if (batch.isEmpty()) {
                    return;
                }
                if (!started) {
                    out.write("[".getBytes(StandardCharsets.UTF_8));
                    started = true;
                } else {
                    out.write(",".getBytes(StandardCharsets.UTF_8));
                }
                Iterator<T> iterator = batch.iterator();
                while (iterator.hasNext()) {
                    out.write(objectMapper.writeValueAsBytes(iterator.next()));
                    if (iterator.hasNext()) {
                        out.write(",".getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        };
    }

    /** JSON 数组结束信封（配合 {@link #jsonArray}；NDJSON 无需）。 */
    public static byte[] jsonEpilogue() {
        return "]".getBytes(StandardCharsets.UTF_8);
    }
}
