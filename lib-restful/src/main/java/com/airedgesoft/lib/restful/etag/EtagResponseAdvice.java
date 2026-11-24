package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.restful.pagination.Page;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * ETag <b>框架侧自动装配</b>（规范 §6.7）：本 advice 在消息转换前完成两件互相独立的事：
 * <ol>
 * <li><b>表示 hash ETag（恒生效）</b>：对最终响应表示（包含 body 内 {@code lockedVersion}）做规范化序列化并取 {@code sha256}，恒下发
 * {@code ETag: "sha256-…"}。同一表示恒得同一 hash，与并发代数无关；</li>
 * <li><b>并发代数注入</b>：控制器返回纯数据读模型（{@code Q*}，无版本字段）时，依端点 {@link EtagResource} 身份声明，从请求级上下文 {@link EtagContext} 取当前代数并包装为
 * {@link Etagged}（wire 平铺携带 {@code lockedVersion}）；{@link Page} 条目逐条同样处理；创建类响应（201）补 {@code Location: /v1/{name}}。</li>
 * </ol>
 *
 * <p>
 * <b>职责分离（决策 C4 / C5）</b>：{@code ETag} 只是<b>响应表示的 hash</b>（缓存校验器），并发控制只依赖 body 中的 {@code lockedVersion}，二者不再互相派生。
 * {@link EnableRepresentationCache} 仅控制是否启用<b>条件读取</b>（评估 {@code If-None-Match} → 304，并下发 {@code Vary} /
 * {@code Cache-Control} 与写表示 hash 缓存）；是否标注注解都不改变 ETag 的 hash 语义。
 *
 * <p>
 * <b>为何在 advice 而非切面装配</b>：控制器方法声明返回类型为 {@code Q*}，CGLIB 代理对返回值做声明类型 checkcast—— 切面若以 {@code Etagged} 替换返回值将抛
 * {@link ClassCastException}；advice 在消息转换前作用于已生成的对象，无此约束。
 *
 * <p>
 * <b>身份未知（未加载 / 未记录版本）→ 仍下发表示 hash ETag，但不注入 {@code lockedVersion}</b>：宁可让客户端拿到无版本的载荷（后续写请求会被 400 拒绝），也不凭空构造版本（错误的
 * {@code "v-1"} 会让写请求恒冲突），并记录 WARN 便于定位。
 */
@ControllerAdvice
public class EtagResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtagResponseAdvice.class);

    private static final String ETAG_HEADER = "ETag";
    private static final String LOCATION_HEADER = "Location";
    private static final String URI_PREFIX = "/v1/";
    private static final String VARY_VALUE = "Authorization, Accept, Accept-Encoding";
    private static final String CACHE_CONTROL_VALUE = "private, no-cache";

    /** 规范化序列化器：字段 / Map 键排序，保证同一表示稳定产出同一 hash。 */
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .serializationInclusion(JsonInclude.Include.NON_NULL).findAndAddModules().build();

    private final EtagHashCache hashCache;
    private final ObjectMapper objectMapper;

    public EtagResponseAdvice() {
        this(new InMemoryEtagHashCache(), CANONICAL_MAPPER);
    }

    public EtagResponseAdvice(EtagHashCache hashCache, ObjectMapper objectMapper) {
        this.hashCache = hashCache == null ? new InMemoryEtagHashCache() : hashCache;
        this.objectMapper = objectMapper == null ? CANONICAL_MAPPER : objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        Method method = returnType == null ? null : returnType.getMethod();
        Object representation = assemble(body, method, returnType, response);
        return applyEtag(representation, response, isRepresentationCacheEnabled(method));
    }

    /** 装配响应表示：单资源 / 集合条目注入 {@code lockedVersion}；创建响应补 {@code Location}。 */
    private Object assemble(Object body, Method method, MethodParameter returnType, ServerHttpResponse response) {
        if (body instanceof Etag tagged) {
            applyLocationForEnvelope(tagged, returnType, response);
            return tagged;
        }
        if (body instanceof Page<?> page) {
            return Page.of(wrapItems(page.items(), method), page.nextPageToken(), page.totalSize());
        }
        return assembleSingle(body, method, returnType, response);
    }

    /** 单资源：解析身份 → 取当前代数 → 必要时包装 {@link Etagged}（HTTP 头由 {@link #applyEtag} 统一装配）。 */
    private Object assembleSingle(Object payload, Method method, MethodParameter returnType,
            ServerHttpResponse response) {
        HttpServletRequest request = EtagIdentities.currentRequest();
        String identity = EtagIdentities.resolve(method, payload, request);
        Long generation = generationOf(identity, payload);
        if (isCreatedResponse(returnType) || statusOf(response) == HttpStatus.CREATED.value()) {
            String name = ResourceName.of(payload);
            if (name != null) {
                setHeader(response, LOCATION_HEADER, URI_PREFIX + name);
            }
        }
        return generation == null ? payload : Etagged.of(payload, generation);
    }

    /** 已显式装配的 {@link Etagged}：创建响应补 {@code Location}（用于切面 / 手工包装形态）。 */
    private void applyLocationForEnvelope(Etag tagged, MethodParameter returnType, ServerHttpResponse response) {
        if (!isCreatedResponse(returnType) && statusOf(response) != HttpStatus.CREATED.value()) {
            return;
        }
        String name = ResourceName.of(tagged);
        if (name == null) {
            throw new IllegalStateException("创建响应载荷缺少 name() 访问器（Location=/v1/{name} 派生源，规范 §2）");
        }
        setHeader(response, LOCATION_HEADER, URI_PREFIX + name);
    }

    /**
     * 逐条把纯数据条目包装为 {@link Etagged}（版本未知时 lockedVersion 省略，序列化后条目仍保留原字段）。
     * 身份解析与单资源同口径（{@link EtagIdentities#resolve}）：子资源集合经端点 {@link EtagResource} / 唯一路径变量 指向<b>父聚合</b>代数，而非条目自身 id。
     */
    private static List<Object> wrapItems(List<?> items, Method method) {
        HttpServletRequest request = EtagIdentities.currentRequest();
        List<Object> wrapped = new ArrayList<>(items.size());
        for (Object item : items) {
            String identity = EtagIdentities.resolve(method, item, request);
            Long generation = generationOf(identity, item);
            wrapped.add(generation == null ? item : Etagged.of(item, generation));
        }
        return wrapped;
    }

    /** 当前对外代数（身份未记录 → null）。未记录告警每请求一次（集合逐条装配场景防日志洪水）。 */
    private static final String MISSING_VERSION_WARNED = EtagResponseAdvice.class.getName() + ".missingVersionWarned";

    private static Long generationOf(String identity, Object payload) {
        if (identity == null) {
            return null;
        }
        Long generation = EtagContext.currentGeneration(identity);
        if (generation == null) {
            HttpServletRequest request = EtagIdentities.currentRequest();
            if (request == null || request.getAttribute(MISSING_VERSION_WARNED) == null) {
                LOGGER.warn("并发身份 {} 未记录当前版本（资源未加载 / 读路径未观测）——不注入 lockedVersion（ETag 仍为表示 hash），载荷={}", identity,
                        payload == null ? "?" : payload.getClass().getSimpleName());
                if (request != null) {
                    request.setAttribute(MISSING_VERSION_WARNED, Boolean.TRUE);
                }
            }
        }
        return generation;
    }

    /**
     * 表示 hash ETag（恒生效）：对最终表示做规范化序列化后取 {@code sha256}。{@link EnableRepresentationCache} 开启时额外评估
     * {@code If-None-Match}（命中 → 304，不写 body）、下发 {@code Vary} / {@code Cache-Control} 并写入表示 hash 缓存。
     */
    private Object applyEtag(Object representation, ServerHttpResponse response, boolean conditionalRead) {
        String etag = representationEtag(representation);
        if (etag == null) {
            return representation;
        }
        setHeader(response, ETAG_HEADER, etag);
        if (!conditionalRead) {
            return representation;
        }
        setHeader(response, "Vary", VARY_VALUE);
        setHeader(response, "Cache-Control", CACHE_CONTROL_VALUE);
        HttpServletRequest request = EtagIdentities.currentRequest();
        String ifNoneMatch = request == null ? null : request.getHeader("If-None-Match");
        if (matches(ifNoneMatch, etag)) {
            if (response instanceof ServletServerHttpResponse servletResponse) {
                servletResponse.getServletResponse().setStatus(HttpStatus.NOT_MODIFIED.value());
            } else {
                response.setStatusCode(HttpStatus.NOT_MODIFIED);
            }
            return null;
        }
        String key = cacheKey(request);
        if (key != null) {
            hashCache.put(key, etag);
        }
        return representation;
    }

    /** 规范化序列化后取 sha256（序列化失败 → null，退化为普通 200，不阻断业务）。 */
    private String representationEtag(Object body) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(body);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return "\"sha256-" + HexFormat.of().formatHex(digest) + "\"";
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JRE 缺少 SHA-256", e);
        } catch (Exception e) {
            LOGGER.warn("表示序列化失败，跳过 ETag（载荷={}）", body == null ? "null" : body.getClass().getSimpleName(), e);
            return null;
        }
    }

    /** If-None-Match 匹配（支持 {@code *} 与逗号分隔列表；弱校验器等价比较）。 */
    private static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        if ("*".equals(ifNoneMatch.trim())) {
            return true;
        }
        String expected = EtagSupport.normalize(etag);
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = EtagSupport.normalize(candidate);
            if (normalized != null && normalized.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 缓存 key：方法 + URI + 查询串 + 授权上下文（不落明文，只入 key）。同一资源不同用户 / 不同掩码 → 不同 key，避免跨权限复用。
     */
    private static String cacheKey(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        String accept = request.getHeader("Accept");
        return "etag:" + request.getMethod() + ":" + request.getRequestURI() + "?" + request.getQueryString() + ":a="
                + Integer.toHexString(String.valueOf(authorization).hashCode()) + ":m="
                + Integer.toHexString(String.valueOf(accept).hashCode());
    }

    private static boolean isRepresentationCacheEnabled(Method method) {
        if (method == null) {
            return false;
        }
        return method.getAnnotation(EnableRepresentationCache.class) != null
                || method.getDeclaringClass().getAnnotation(EnableRepresentationCache.class) != null;
    }

    private static boolean isCreatedResponse(MethodParameter returnType) {
        if (returnType == null) {
            return false;
        }
        org.springframework.web.bind.annotation.ResponseStatus status = returnType
                .getMethodAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
        if (status != null && (status.value() == HttpStatus.CREATED || status.code() == HttpStatus.CREATED)) {
            return true;
        }
        Method method = returnType.getMethod();
        if (method != null) {
            org.springframework.web.bind.annotation.ResponseStatus classStatus = method.getDeclaringClass()
                    .getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
            return classStatus != null
                    && (classStatus.value() == HttpStatus.CREATED || classStatus.code() == HttpStatus.CREATED);
        }
        return false;
    }

    /**
     * 写响应头：Servlet 宿主直写容器响应（{@code ServletServerHttpResponse} 的 HttpHeaders 视图在<b>写 body 时</b>才拷贝到容器—— 直写保证无 body
     * 路径下头不丢失）； 其他宿主走 HttpHeaders。
     */
    private static void setHeader(ServerHttpResponse response, String name, String value) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setHeader(name, value);
            return;
        }
        response.getHeaders().set(name, value);
    }

    /** 响应状态码（Servlet 容器形态；非 Servlet 宿主 → 0）。 */
    private static int statusOf(ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            return servletResponse.getServletResponse().getStatus();
        }
        return 0;
    }
}
