package com.airedgesoft.lib.restful.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StreamingExportSupport 单测（组件 11；分批游标拉取与消费写出——直接驱动 StreamingResponseBody，不起容器）。
 */
class StreamingExportSupportTest {

    @Test
    void stream_pagesUntilMissingNextCursor() throws Exception {
        List<List<String>> pages = List.of(List.of("a", "b"), List.of("c"), List.of());
        List<List<String>> fetched = new java.util.ArrayList<>();
        StreamingResponseBody body = StreamingExportSupport.stream(cursor -> {
            fetched.add(cursor == null ? pages.get(0) : cursor.equals("c1") ? pages.get(1) : pages.get(2));
            return new StreamingExportSupport.Page<>(fetched.get(fetched.size() - 1),
                    fetched.size() == 1 ? "c1" : null);
        }, (out, batch) -> out.write(String.join("|", batch).getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("a|bc"); // 每批一次消费（批间无分隔符）
        assertThat(fetched).hasSize(2); // nextCursor=null 即终止（空批不触发）
    }

    @Test
    void jsonLines_oneObjectPerRow() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StreamingResponseBody body = StreamingExportSupport.stream(
                cursor -> new StreamingExportSupport.Page<>(List.of(new Row("齿轮箱", 3), new Row("联轴器", 7)), null),
                StreamingExportSupport.jsonLines(mapper));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        String[] lines = out.toString(StandardCharsets.UTF_8).trim().split("\n");
        assertThat(lines).hasSize(2);
        assertThat(mapper.readValue(lines[0], Row.class).title()).isEqualTo("齿轮箱");
    }

    /** 序列化样例行（静态嵌套——Jackson 反射实例化要求）。 */
    public record Row(String title, int qty) {
    }

    @Test
    void jsonArray_spansBatchesWithEpilogue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StreamingResponseBody body = StreamingExportSupport.stream(
                cursor -> cursor == null
                        ? new StreamingExportSupport.Page<>(List.of(1, 2), "next")
                        : new StreamingExportSupport.Page<>(List.of(3), null),
                StreamingExportSupport.jsonArray(mapper));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        out.writeBytes(StreamingExportSupport.jsonEpilogue());
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("[1,2,3]");
    }
}
