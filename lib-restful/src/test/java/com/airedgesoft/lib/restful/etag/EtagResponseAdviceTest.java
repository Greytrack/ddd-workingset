package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.restful.pagination.Page;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EtagResponseAdvice} 单测：控制器返回纯数据 {@code Q*} 时，框架依 {@link EtagResource} 身份声明把当前并发代数平铺进
 * wire（{@code lockedVersion}）， 并恒下发<b>表示 hash</b> {@code ETag: "sha256-…"}。 {@link EnableRepresentationCache}
 * 只控制条件读取（命中 {@code If-None-Match} → 304 + Vary / Cache-Control），不改变 ETag 语义。
 */
class EtagResponseAdviceTest {

    /** 模拟读模型：具 AIP-122 {@code name()} 访问器，无版本字段。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QTask(String name, String taskId, String title) {
    }

    private final EtagResponseAdvice advice = new EtagResponseAdvice();

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void reset() {
        EtagContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void singleResource_wrapsWithGeneration_andEtagIsRepresentationHash() throws Exception {
        request("/v1/demoTasks/101", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 2L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new QTask("demoTasks/101", "101", "T"), parameter("get"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Etagged.class);
        assertThat(((Etagged<?>) body).lockedVersion()).isEqualTo(3L);
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-").endsWith("\"");
        assertThat(mapper.writeValueAsString(body))
                .isEqualTo("{\"name\":\"demoTasks/101\",\"taskId\":\"101\",\"title\":\"T\",\"lockedVersion\":3}");
    }

    @Test
    void unknownIdentity_stillEmitsRepresentationHash_butOmitsLockedVersion() {
        request("/v1/demoTasks/999", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new QTask("demoTasks/999", "999", "T"), parameter("get"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(QTask.class);
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
    }

    @Test
    void collection_carriesLockedVersionInBody_andEtagIsRepresentationHash() {
        request("/v1/demoTasks", null);
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        EtagContext.recordLoaded("demoTasks/102", new Object(), 1L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(
                new Page<>(List.of(new QTask("demoTasks/101", "101", "A"), new QTask("demoTasks/102", "102", "B")),
                        null, 2),
                parameter("list"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Page.class);
        Page<?> page = (Page<?>) body;
        assertThat(page.items().get(0)).isInstanceOf(Etagged.class);
        assertThat(((Etagged<?>) page.items().get(0)).lockedVersion()).isEqualTo(1L);
        assertThat(((Etagged<?>) page.items().get(1)).lockedVersion()).isEqualTo(2L);
        // ETag 恒为整页表示的 hash；逐条令牌响应头已移除
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("ETag-Items")).isNull();
    }

    @Test
    void collection_missingGenerationEntry_leftUnwrapped_etagStillHash() {
        request("/v1/demoTasks", null);
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(
                new Page<>(List.of(new QTask("demoTasks/101", "101", "A"), new QTask("demoTasks/999", "999", "X")),
                        null, 2),
                parameter("list"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Page.class);
        Page<?> page = (Page<?>) body;
        assertThat(page.items().get(0)).isInstanceOf(Etagged.class);
        assertThat(page.items().get(1)).isInstanceOf(QTask.class);
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("ETag-Items")).isNull();
    }

    @Test
    void projectionPage_etagIsRepresentationHash_noItemTokens() {
        request("/v1/demoTasks/101/projection", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new Page<>(List.of(new QTask("demoTasks/101", "101", "A")), null, 1),
                parameter("projection"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Page.class);
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("ETag-Items")).isNull();
    }

    @Test
    void withoutAnnotation_ifNoneMatchIgnored_returns200WithBody() {
        // 未标注 @EnableRepresentationCache：不评估 If-None-Match，恒 200 + 响应体；ETag 仍为 hash
        request("/v1/demoTasks/101/projection", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        currentRequest().addHeader("If-None-Match", "\"v-1\"");

        Object body = advice.beforeBodyWrite(new Page<>(List.of(new QTask("demoTasks/101", "101", "A")), null, 1),
                parameter("projection"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Page.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("Vary")).isNull();
    }

    @Test
    void withAnnotation_firstRequest200WithEtag() {
        request("/v1/demoTasks/101", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new QTask("demoTasks/101", "101", "T"), parameter("cachedGet"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isInstanceOf(Etagged.class);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("Vary")).isEqualTo("Authorization, Accept, Accept-Encoding");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-cache");
    }

    @Test
    void withAnnotation_matchingIfNoneMatch_returns304WithoutBody() {
        // 首次请求取回 hash ETag
        request("/v1/demoTasks/101", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse first = new MockHttpServletResponse();
        advice.beforeBodyWrite(new QTask("demoTasks/101", "101", "T"), parameter("cachedGet"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(first));
        String etag = first.getHeader("ETag");
        assertThat(etag).startsWith("\"sha256-");

        // 同一表示 + If-None-Match: 命中 → 304、无 body
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/v1/demoTasks/101");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of("taskId", "101"));
        request.addHeader("If-None-Match", etag);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new QTask("demoTasks/101", "101", "T"), parameter("cachedGet"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(body).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_MODIFIED.value());
        assertThat(response.getHeader("ETag")).isEqualTo(etag);
    }

    @Test
    void createResponse_usesOwnIdentityFromName_emitsEtagAndLocation() {
        request("/v1/demoTasks", null);
        EtagContext.recordSaved("demoTasks/777", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(new QTask("demoTasks/777", "777", "T"), parameter("create"),
                MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        assertThat(((Etagged<?>) body).lockedVersion()).isEqualTo(1L);
        assertThat(response.getHeader("ETag")).startsWith("\"sha256-");
        assertThat(response.getHeader("Location")).isEqualTo("/v1/demoTasks/777");
    }

    @Test
    void subResourcePage_itemsCarryParentGeneration() {
        // 子资源集合：条目 name 指向 step，但并发代数归属父任务（@EtagResource("taskId")）——P1 回归
        request("/v1/demoTasks/101/taskSteps", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 2L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(
                new Page<>(List.of(new QTask("demoTasks/101/taskSteps/1", "1", "S1"),
                        new QTask("demoTasks/101/taskSteps/2", "2", "S2")), null, 2),
                parameter("projection"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        Page<?> page = (Page<?>) body;
        assertThat(((Etagged<?>) page.items().get(0)).lockedVersion()).isEqualTo(3L);
        assertThat(((Etagged<?>) page.items().get(1)).lockedVersion()).isEqualTo(3L);
    }

    @Test
    void uniquePathVariable_fallbackResolvesParentWithoutAnnotation() {
        request("/v1/demoTasks/101/taskSteps", "101");
        EtagContext.recordLoaded("demoTasks/101", new Object(), 0L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object body = advice.beforeBodyWrite(
                new Page<>(List.of(new QTask("demoTasks/101/taskSteps/1", "1", "S1")), null, 1),
                parameter("stepsNoAnnotation"), MediaType.APPLICATION_JSON, MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(currentRequest()), new ServletServerHttpResponse(response));

        Page<?> page = (Page<?>) body;
        assertThat(((Etagged<?>) page.items().get(0)).lockedVersion()).isEqualTo(1L);
    }

    // ── 辅助 ──

    /** 端点方法签名（决定 {@link EtagResource} 声明的读取）。 */
    @SuppressWarnings("unused")
    static class SampleController {

        @EtagResource("taskId")
        QTask get(@org.springframework.web.bind.annotation.PathVariable("taskId") String taskId) {
            return null;
        }

        @EtagResource("taskId")
        @EnableRepresentationCache
        QTask cachedGet(@org.springframework.web.bind.annotation.PathVariable("taskId") String taskId) {
            return null;
        }

        @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
        QTask create() {
            return null;
        }

        Page<QTask> list() {
            return null;
        }

        @EtagResource("taskId")
        Page<QTask> projection(@org.springframework.web.bind.annotation.PathVariable("taskId") String taskId) {
            return null;
        }

        Page<QTask> stepsNoAnnotation(@org.springframework.web.bind.annotation.PathVariable("taskId") String taskId) {
            return null;
        }
    }

    private static MethodParameter parameter(String method) {
        try {
            java.lang.reflect.Method reflected = SampleController.class.getDeclaredMethod(method,
                    takesTaskId(method) ? new Class<?>[]{String.class} : new Class<?>[0]);
            return new MethodParameter(reflected, -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean takesTaskId(String method) {
        return "get".equals(method) || "projection".equals(method) || "cachedGet".equals(method)
                || "stepsNoAnnotation".equals(method);
    }

    private static MockHttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return (MockHttpServletRequest) attributes.getRequest();
        }
        return null;
    }

    /** 建立请求上下文（含 URI 模板变量，模拟 DispatcherServlet 绑定结果）。 */
    private static void request(String uri, String taskId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI(uri);
        if (taskId != null) {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of("taskId", taskId));
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
    }
}
