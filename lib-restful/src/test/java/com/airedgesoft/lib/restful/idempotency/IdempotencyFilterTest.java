package com.airedgesoft.lib.restful.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IdempotencyFilter 作用域契约单测（规范 §6.5）：携带 {@code Idempotency-Key} 的 POST 一律防护——无请求体 / 未传 Content-Type 记空串指纹参与防护 （无体命令端点如
 * {@code :activate}）；显式非 JSON Content-Type（multipart 等）跳过防护；无 key / 空白 key 照常放行。
 */
class IdempotencyFilterTest {

    private static final String ACTIVATE_URI = "/v1/demoTasks/1:activate";
    private static final String CREATE_URI = "/v1/demoTasks";

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyFilter filter = new IdempotencyFilter(store, new ObjectMapper());

    @DisplayName("幂等回放：无体命令端点带 key 首见放行执行，重试回放缓照不重复执行并标记 X-Idempotency-Replay")
    @Test
    void post_noBodyWithKey_firstSeenExecutes_thenRetryReplaysWithoutReexecution() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest(ACTIVATE_URI, null, "k1", new byte[0]), firstResponse, chain);
        assertThat(executions.get()).isEqualTo(1); // 首见（空串指纹）放行执行
        assertThat(firstResponse.getStatus()).isEqualTo(200);

        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        filter.doFilter(postRequest(ACTIVATE_URI, null, "k1", new byte[0]), replayResponse, chain);
        assertThat(executions.get()).isEqualTo(1); // 回放缓照，不重复执行
        assertThat(replayResponse.getStatus()).isEqualTo(200);
        assertThat(replayResponse.getHeader("X-Idempotency-Replay")).isEqualTo("true");
    }

    @DisplayName("幂等并发：同 key 请求处理中再入 → 直接 409 problem+json，不经业务链执行")
    @Test
    void post_noBodyWithKey_inFlightConcurrency_returns409Problem() throws Exception {
        // 前序同 key 同指纹（空串）请求处理中（占位未 complete）：filter 直接 409，不经业务链
        store.begin("POST " + ACTIVATE_URI, "k2", "");
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(postRequest(ACTIVATE_URI, null, "k2", new byte[0]), response, chain);
        assertThat(executions.get()).isZero();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentType()).startsWith("application/problem+json");
    }

    @DisplayName("幂等回放：同 key 同 JSON body 重试回放缓照，业务链仅执行一次")
    @Test
    void post_jsonBodyWithKey_firstSeenExecutes_thenRetryReplays() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();
        byte[] body = "{\"name\":\"demo-task\"}".getBytes(StandardCharsets.UTF_8);

        filter.doFilter(postRequest(CREATE_URI, "application/json", "k3", body), new MockHttpServletResponse(), chain);
        filter.doFilter(postRequest(CREATE_URI, "application/json", "k3", body), new MockHttpServletResponse(), chain);
        assertThat(executions.get()).isEqualTo(1); // JSON 载荷契约不变：同 key 同 body 回放
    }

    /** 预检结果不能替代同键同正文的写入结果。 */
    @Test
    void post_validateOnlyCannotReplayAsWrite() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();
        byte[] body = "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);
        var preview = postRequest(ACTIVATE_URI, "application/json", "preview-key", body);
        preview.addParameter("validateOnly", "true");

        filter.doFilter(preview, new MockHttpServletResponse(), chain);
        var response = new MockHttpServletResponse();
        filter.doFilter(postRequest(ACTIVATE_URI, "application/json", "preview-key", body), response, chain);

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getHeader("X-Idempotency-Replay")).isNull();
        assertThat(executions.get()).isEqualTo(1);
    }

    @DisplayName("幂等豁免：multipart 等显式非 JSON Content-Type 跳过防护，重复请求照常执行两次")
    @Test
    void post_multipartWithKey_skipsProtectionAndExecutesTwice() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();
        String contentType = "multipart/form-data; boundary=boundary";

        filter.doFilter(postRequest(ACTIVATE_URI, contentType, "k4", new byte[0]), new MockHttpServletResponse(),
                chain);
        filter.doFilter(postRequest(ACTIVATE_URI, contentType, "k4", new byte[0]), new MockHttpServletResponse(),
                chain);
        assertThat(executions.get()).isEqualTo(2); // 文档化豁免：显式非 JSON 写载荷两次照常执行
    }

    @DisplayName("幂等豁免：超限 JSON 大报文跳过指纹防护，重复请求照常执行两次")
    @Test
    void post_jsonBodyOverLimitWithKey_skipsProtectionAndExecutesTwice() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();
        byte[] oversize = new byte[1024 * 1024 + 1];

        filter.doFilter(postRequest(CREATE_URI, "application/json", "k5", oversize), new MockHttpServletResponse(),
                chain);
        filter.doFilter(postRequest(CREATE_URI, "application/json", "k5", oversize), new MockHttpServletResponse(),
                chain);
        assertThat(executions.get()).isEqualTo(2); // 仅超限跳过（防大报文指纹成本），照常执行
    }

    @DisplayName("chunked 超限透传：无 Content-Length 大报文跳过防护照常执行，且下游经「前缀 + 原始流续传」拿到完整 body")
    @Test
    void post_chunkedBodyOverLimitWithKey_skipsProtectionAndStreamsFullBody() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        List<byte[]> received = new ArrayList<>();
        FilterChain chain = (req, res) -> {
            received.add(StreamUtils.copyToByteArray(req.getInputStream()));
            executions.incrementAndGet();
        };
        byte[] oversize = new byte[1024 * 1024 + 1];
        // 两次独立请求（真实重试 = 新报文；同一 ServletInputStream 不可重读，不能复用 wrapper）
        filter.doFilter(chunkedRequest(oversize), new MockHttpServletResponse(), chain);
        filter.doFilter(chunkedRequest(oversize), new MockHttpServletResponse(), chain);

        assertThat(executions.get()).isEqualTo(2); // 超指纹上限：跳过防护照常执行（不缓存不回放）
        assertThat(received.get(0)).isEqualTo(oversize); // 前缀缓冲 + 原始流续传 → 下游 body 完整无截断
        assertThat(received.get(1)).isEqualTo(oversize);
    }

    @DisplayName("幂等豁免：不带 Idempotency-Key 的 POST 不去重，每次照常执行")
    @Test
    void post_withoutKey_passesThroughEveryTime() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();

        filter.doFilter(postRequest(CREATE_URI, "application/json", null, new byte[0]), new MockHttpServletResponse(),
                chain);
        filter.doFilter(postRequest(CREATE_URI, "application/json", null, new byte[0]), new MockHttpServletResponse(),
                chain);
        assertThat(executions.get()).isEqualTo(2); // 不带 key = 不去重照常执行（界面客户端可选携带）
    }

    @DisplayName("幂等豁免：空白 Idempotency-Key 视同未携带，照常放行执行")
    @Test
    void post_blankKey_passesThrough() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();

        filter.doFilter(postRequest(ACTIVATE_URI, null, "  ", new byte[0]), new MockHttpServletResponse(), chain);
        assertThat(executions.get()).isEqualTo(1);
    }

    @DisplayName("409 渲染携带 X-Request-Id：problem+json 的 requestId 取自请求头（与 ProblemDetails 同一口径）")
    @Test
    void post_inFlightConflict_problemCarriesRequestIdHeader() throws Exception {
        store.begin("POST " + ACTIVATE_URI, "k-rid", "");
        MockHttpServletRequest request = postRequest(ACTIVATE_URI, null, "k-rid", new byte[0]);
        request.addHeader("X-Request-Id", "req-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("\"requestId\":\"req-abc-123\"");
    }

    @DisplayName("异常释放 key：链上 ServletException 后 key 被释放，同 key 重试可重新执行而非恒 409")
    @Test
    void post_chainThrowsServletException_keyReleasedForRetry() throws Exception {
        FilterChain failing = (req, res) -> {
            throw new jakarta.servlet.ServletException("下游链路故障");
        };
        byte[] body = "{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockHttpServletRequest first = postRequest(CREATE_URI, "application/json", "k-release", body);
        org.assertj.core.api.Assertions.assertThatExceptionOfType(jakarta.servlet.ServletException.class)
                .isThrownBy(() -> filter.doFilter(first, new MockHttpServletResponse(), failing));

        // key 已释放：同 key 同 body 重试重新执行（若未释放，begin 命中处理中占位 → 409）
        AtomicInteger executions = new AtomicInteger();
        filter.doFilter(postRequest(CREATE_URI, "application/json", "k-release", body), new MockHttpServletResponse(),
                (req, res) -> executions.incrementAndGet());
        assertThat(executions.get()).isEqualTo(1);
    }

    @DisplayName("幂等回放还原附加头：条件读取端点的 Vary / Cache-Control 随快照回放（P2 回归）")
    @Test
    void post_replayRestoresCacheHeaders() throws Exception {
        byte[] body = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        FilterChain chain = (req, res) -> {
            jakarta.servlet.http.HttpServletResponse http = (jakarta.servlet.http.HttpServletResponse) res;
            http.setHeader("Vary", "Authorization, Accept, Accept-Encoding");
            http.setHeader("Cache-Control", "private, no-cache");
            http.setStatus(200);
            http.getWriter().write("{\"a\":1}");
        };
        filter.doFilter(postRequest(CREATE_URI, "application/json", "k-headers", body), new MockHttpServletResponse(),
                chain);

        MockHttpServletResponse replay = new MockHttpServletResponse();
        filter.doFilter(postRequest(CREATE_URI, "application/json", "k-headers", body), replay, chain);
        assertThat(replay.getHeader("X-Idempotency-Replay")).isEqualTo("true");
        assertThat(replay.getHeader("Vary")).isEqualTo("Authorization, Accept, Accept-Encoding");
        assertThat(replay.getHeader("Cache-Control")).isEqualTo("private, no-cache");
    }

    @DisplayName("读语义 POST 豁免：:wait 长轮询带 key 也不回放，每次照常执行")
    @Test
    void post_waitVerb_skipsIdempotencyReplay() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        FilterChain chain = (req, res) -> executions.incrementAndGet();
        String uri = "/v1/operations/1:wait";
        filter.doFilter(postRequest(uri, "application/json", "k-wait", new byte[0]), new MockHttpServletResponse(),
                chain);
        filter.doFilter(postRequest(uri, "application/json", "k-wait", new byte[0]), new MockHttpServletResponse(),
                chain);
        assertThat(executions.get()).isEqualTo(2);
    }

    /** POST 请求构造（contentType / key 可空：null = 未传，覆盖「无 Content-Type / 无 key」作用域分支）。 */
    /** 模拟 chunked 上传：报文已在缓冲，但长度未知（getContentLength 返回 -1），故不能走长度预检、只能有界读体判定。 */
    private static HttpServletRequest chunkedRequest(byte[] body) {
        MockHttpServletRequest request = postRequest(CREATE_URI, "application/json", "k-chunked", body);
        return new HttpServletRequestWrapper(request) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
    }

    private static MockHttpServletRequest postRequest(String uri, String contentType, String key, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        if (contentType != null) {
            request.setContentType(contentType);
        }
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        request.setContent(body);
        return request;
    }
}
