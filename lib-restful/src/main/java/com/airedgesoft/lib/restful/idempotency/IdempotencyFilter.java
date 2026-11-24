package com.airedgesoft.lib.restful.idempotency;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.problem.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * <b>幂等防护 = 框架默认行为</b>（v4-restful-api.md §6.5，2026-10 规范修订：退役 {@code @Idempotent} 注解接入）： 所有 {@code POST} 端点（创建 / 批量创建 /
 * {@code :{verb}} 命令）无需任何注解即自动受幂等防护—— 前端传递 {@code Idempotency-Key} 即处理（携带才去重，不带 key = 照常执行）；GET / PATCH / DELETE 不在本
 * filter 范围（天然幂等或由 {@code lockedVersion} 并发护栏承担）。
 *
 * <p>
 * <b>为什么从注解 AOP 改为 Filter</b>：幂等是 HTTP 协议关注点，属于传输层而非控制器方法签名； filter 形态使控制层<b>零注解零代码</b>，且回放缓存的是 <b>字节级响应快照</b>（状态码 + 头
 * + body 字节），比切面缓存 DTO 对象再二次序列化更保真。
 *
 * <p>
 * <b>契约</b>（与 {@link IdempotencyStore} 三阶段语义一致）：
 * <ul>
 * <li>作用域：携带 {@code Idempotency-Key} 的 {@code POST} 一律防护——无请求体 / 未传 {@code Content-Type}（如 {@code :activate}
 * 类无体命令端点）亦参与， 指纹记空串；仅未带 key，或显式声明非 {@code application/json} 的 {@code Content-Type}（multipart 等，文档化豁免）跳过；</li>
 * <li>命名空间：{@code "POST " + requestURI}（按端点 + 实例隔离，等价原 operationId 隔离语义且更严格）；</li>
 * <li>指纹：通常使用请求体原文，启用 {@code validateOnly} 时加入预检语义。正文按 UTF-8 读取，上限 1 MiB；
 * 超限照常执行，无长度请求以「前缀缓冲 + 原始流续传」透传，保持缓冲内存有界；</li>
 * <li>无 key / 同 key 同 body 首见 → 放行执行，2xx/3xx 缓存快照；4xx/5xx 与业务异常<b>不缓存</b>并释放 key（可原 key 重试）；</li>
 * <li>同 key 同 body 重试 → 回放快照（状态码 + Location + ETag + 附加头（{@code Vary} / {@code Cache-Control}）+
 * {@code X-Idempotency-Replay: true} + 原字节）；</li>
 * <li>读语义 POST（如 LRO {@code :wait}）豁免防护——长轮询每次取当前快照，不回放；</li>
 * <li>同 key 异 body → 422、处理中并发 → 409（{@link IdempotencyStore#begin} 抛 {@link DomainException}， 本 filter 在 handler
 * 之前执行、不经 {@code @RestControllerAdvice}，故自行渲染 RFC 9457 problem+json， 与 {@link ProblemDetails} 统一构建）；</li>
 * <li>注册顺序 {@code HIGHEST_PRECEDENCE + 5}：最外层，快照天然包含内层 ETag / Location 等最终响应头。</li>
 * </ul>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    /** 幂等键请求头（框架内跨组件共用的唯一常量）。 */
    public static final String KEY_HEADER = "Idempotency-Key";
    private static final String REPLAY_HEADER = "X-Idempotency-Replay";
    private static final String LOCATION_HEADER = "Location";
    private static final String ETAG_HEADER = "ETag";
    private static final int MAX_FINGERPRINT_BODY_BYTES = 1024 * 1024;
    private static final String VALIDATE_ONLY_PARAMETER = "validateOnly";
    private static final String VALIDATE_ONLY_FINGERPRINT_PREFIX = "\u0000validateOnly=true\n";
    private static final Set<String> TRUE_QUERY_VALUES = Set.of("true", "on", "yes", "1");

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅 POST 受防护：创建 / 批量创建 / 冒号命令端点全部为 POST（规范 §4/§4.1/§6.5）；
        // 读语义 POST（如 LRO `:wait` 长轮询）不回放——每次等待都应取当前快照，回放旧快照会冻结进度
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String verb : READ_SEMANTIC_VERBS) {
            if (uri != null && uri.toLowerCase(java.util.Locale.ROOT).endsWith(":" + verb)) {
                return true;
            }
        }
        return false;
    }

    /** 读语义冒号动词（POST 承载但无写副作用，豁免幂等回放）。 */
    private static final java.util.Set<String> READ_SEMANTIC_VERBS = java.util.Set.of("wait");

    /** 需随回放还原的附加响应头（条件读取端点的缓存语义头）。 */
    private static final String[] REPLAYED_HEADERS = {"Vary", "Cache-Control"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(KEY_HEADER);
        String contentType = request.getContentType();
        boolean nonJsonBody = contentType != null && !contentType.toLowerCase().startsWith("application/json");
        if (key == null || key.isBlank() || nonJsonBody) {
            // 无 key = 不去重照常执行（界面客户端可选携带，§18 M5）；显式非 JSON 写载荷（multipart 等）跳过防护（文档化豁免）
            chain.doFilter(request, response);
            return;
        }

        // 指纹成本上限（防大报文）：Content-Length 已知超限时在读体缓冲前直接跳过（免整包驻留堆内）；无长度（分块传输）时读体后按字节数判定
        if (request.getContentLengthLong() > MAX_FINGERPRINT_BODY_BYTES) {
            chain.doFilter(request, response);
            return;
        }
        CachedHttpServletRequest cachedRequest = new CachedHttpServletRequest(request, MAX_FINGERPRINT_BODY_BYTES);
        if (cachedRequest.isOverflowed()) {
            // chunked（无 Content-Length）超指纹上限：有界读体已停读 → 以「前缀缓冲 + 原始流续传」透传
            // （跳过防护照常执行，§6.5 超限语义；下游拿到完整 body，filter 内存占用有界于指纹上限）
            chain.doFilter(cachedRequest, response);
            return;
        }
        String fingerprint = fingerprint(cachedRequest);
        String namespace = namespace(request);

        IdempotencyStore.CachedResponse cached;
        try {
            cached = store.begin(namespace, key, fingerprint);
        } catch (DomainException ex) {
            // 422 指纹不符 / 409 处理中并发：filter 层不经 @RestControllerAdvice，自行渲染 problem+json（§8 同一构建收口）
            renderProblem(request, response, ex);
            return;
        }
        if (cached != null) {
            replay(response, cached);
            return;
        }

        // 响应体缓存包装：完成时读取字节级快照（含 ETag/Location 最终响应头——header 直写底层 response，wrapper 委托可读）
        ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(cachedRequest, cachingResponse);
        } catch (RuntimeException | ServletException | IOException e) {
            // 失败不缓存：任何异常路径（业务异常 /ServletException/IOException）都释放 key 允许同 key 重试，
            // 否则 key 停留处理中直至 TTL，期间同 key 重试恒 409（异常继续上抛走既有异常链）；
            // Error（OOM 等）不在此兜底——release 亦可能不可靠，按尽力而为口径交由容器终止
            store.release(namespace, key);
            throw e;
        }
        int status = cachingResponse.getStatus();
        if (status >= HttpStatus.OK.value() && status < HttpStatus.MULTIPLE_CHOICES.value()) {
            java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
            for (String name : REPLAYED_HEADERS) {
                String value = cachingResponse.getHeader(name);
                if (value != null) {
                    headers.put(name, value);
                }
            }
            store.complete(namespace, key,
                    new IdempotencyStore.CachedResponse(status, cachingResponse.getHeader(LOCATION_HEADER),
                            cachingResponse.getHeader(ETAG_HEADER),
                            new String(cachingResponse.getContentAsByteArray(), StandardCharsets.UTF_8),
                            headers.isEmpty() ? null : headers));
        } else {
            // 4xx/5xx = 失败不缓存（规范 §6.5：释放 key，允许原 key 重试）
            store.release(namespace, key);
        }
        cachingResponse.copyBodyToResponse();
    }

    private void replay(HttpServletResponse response, IdempotencyStore.CachedResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.headers() != null) {
            cached.headers().forEach(response::setHeader);
        }
        if (cached.location() != null) {
            response.setHeader(LOCATION_HEADER, cached.location());
        }
        if (cached.eTag() != null) {
            response.setHeader(ETAG_HEADER, cached.eTag());
        }
        response.setHeader(REPLAY_HEADER, "true");
        if (cached.body() != null) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(String.valueOf(cached.body()));
        }
    }

    /** 将预检标志计入请求指纹，防止把预检响应回放为实际写入成功。 */
    private static String fingerprint(CachedHttpServletRequest request) {
        String validateOnly = request.getParameter(VALIDATE_ONLY_PARAMETER);
        String body = request.bodyAsString();
        if (validateOnly != null && TRUE_QUERY_VALUES.contains(validateOnly.trim().toLowerCase(Locale.ROOT))) {
            return VALIDATE_ONLY_FINGERPRINT_PREFIX + body;
        }
        return body;
    }

    private void renderProblem(HttpServletRequest request, HttpServletResponse response, DomainException ex)
            throws IOException {
        ProblemDetail problem = ProblemDetails.build(ex,
                ProblemDetails.requestIdOrUnknown(request.getHeader(ProblemDetails.REQUEST_ID_HEADER)));
        response.setStatus(problem.getStatus());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    /** 命名空间：{@code POST + requestURI}（按端点 + 实例隔离；不依赖注解/路由元数据，规范 §6.5）。 */
    private String namespace(HttpServletRequest request) {
        return HttpMethod.POST.name() + ' ' + request.getRequestURI();
    }

    /**
     * 可重复读请求体（有界缓冲；指纹读取后链上处理器仍可消费原始字节）。 超出指纹上限（chunked 无长度预检命中场景）只缓冲前缀，下游经「前缀 + 原始流续传」拿到完整 body—— filter 内存占用始终有界于指纹上限。
     *
     * <p>
     * <b>重读语义</b>：未溢出的请求体可重复读（每次新建字节流）；<b>溢出的请求体只保证单次消费</b>——尾部是原始流（位置已前进），二次读取会重放前缀而拿不到后缀。
     * 转换器应只读一次；需要全文多次消费的场景不应走「超指纹上限透传」路径（本 filter 明确按「跳过防护、单次透传」处理，§6.5）。
     */
    private static final class CachedHttpServletRequest extends HttpServletRequestWrapper {

        /** 已读前缀（恒非 null；溢出时长度为 maxBytes）。 */
        private final byte[] head;
        /** 判定溢出时多读的那 1 字节（须原样续供给下游，否则正文丢字节）。 */
        private final byte[] carried;
        private final boolean overflowed;

        CachedHttpServletRequest(HttpServletRequest request, int maxBytes) throws IOException {
            super(request);
            ServletInputStream in = request.getInputStream();
            Buffered buffered = in == null ? Buffered.empty() : copyBounded(in, maxBytes);
            this.head = buffered.head();
            this.carried = buffered.carried();
            this.overflowed = buffered.overflowed();
        }

        /** 有界读体结果：前缀 + 判定溢出时多读的尾字节 + 是否溢出。 */
        private record Buffered(byte[] head, byte[] carried, boolean overflowed) {

            static Buffered empty() {
                return new Buffered(new byte[0], new byte[0], false);
            }
        }

        /**
         * 有界读体：最多读 {@code maxBytes + 1} 字节即停（内存占用恒有界）。≤maxBytes → 完整 body；&gt;maxBytes → 前 maxBytes 字节为前缀、第 maxBytes+1
         * 字节单独携带，其余字节留在原始流中由下游续读——两者都由 {@link #getInputStream()} 原样续供。
         */
        private static Buffered copyBounded(ServletInputStream in, int maxBytes) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            while (true) {
                int room = maxBytes - total + 1; // +1：多读一字节即可判定溢出
                if (room <= 0) {
                    break;
                }
                int n = in.read(buf, 0, Math.min(buf.length, room));
                if (n == -1) {
                    break;
                }
                out.write(buf, 0, n);
                total += n;
                if (total > maxBytes) {
                    break;
                }
            }
            byte[] read = out.toByteArray();
            if (total > maxBytes) {
                return new Buffered(Arrays.copyOf(read, maxBytes), Arrays.copyOfRange(read, maxBytes, read.length),
                        true);
            }
            return new Buffered(read, new byte[0], false);
        }

        boolean isOverflowed() {
            return overflowed;
        }

        String bodyAsString() {
            return new String(head, StandardCharsets.UTF_8);
        }

        /**
         * 字符流读数（表单 / reader 型转换器）：正文已在本类构造期被读走，必须基于 {@link #getInputStream()} 重建，否则下游拿到空正文。
         */
        @Override
        public java.io.BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),
                    encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (!overflowed) {
                ByteArrayInputStream buffer = new ByteArrayInputStream(head);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return buffer.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return buffer.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
            // 超限透传：按「缓冲前缀 → 判定溢出时多读的尾字节 → 原始流剩余字节」顺序续供，下游拿到完整 body（不丢不重）
            ByteArrayInputStream[] prefixStreams = {new ByteArrayInputStream(head), new ByteArrayInputStream(carried)};
            ServletInputStream rest = super.getInputStream();
            return new ServletInputStream() {

                /** 当前已耗尽的前缀流下标（0=前缀，1=尾字节；到 2 表示转入原始流）。 */
                private int index;

                @Override
                public int read() throws IOException {
                    while (index < prefixStreams.length) {
                        int b = prefixStreams[index].read();
                        if (b != -1) {
                            return b;
                        }
                        index++;
                    }
                    return rest.read();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    while (index < prefixStreams.length) {
                        int n = prefixStreams[index].read(b, off, len);
                        if (n != -1) {
                            return n;
                        }
                        index++;
                    }
                    return rest.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    for (int i = index; i < prefixStreams.length; i++) {
                        if (prefixStreams[i].available() > 0) {
                            return false;
                        }
                    }
                    return rest.isFinished();
                }

                @Override
                public boolean isReady() {
                    return rest.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    rest.setReadListener(listener);
                }
            };
        }
    }
}
