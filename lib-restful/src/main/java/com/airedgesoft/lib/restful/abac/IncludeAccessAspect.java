package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.restful.etag.Etag;
import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@code _access} 伴随与读取路径脱敏切面（§13 / §19.2；定型方案裁决「脱敏下沉集合端点」）：拦截 {@code @RestController} 返回值，按运行时形态分派——
 * <ol>
 * <li><b>脱敏恒生效（读取路径全覆盖）</b>：无权字段 Omit（{@link AccessMasker}）不依赖 {@code includeAccess} 开关（§19.2「脱敏读取路径恒生效」、反模式 「脱敏挂条件」）：
 * <ul>
 * <li>单资源（{@link Etag} / {@link Etagged}）→ mask 内层载荷；</li>
 * <li>{@link Page} → 逐条目 mask（record 不可变，有条目被掩时重建 Page，{@code nextPageToken} / {@code totalSize} 保留；无变化原样透传）；</li>
 * <li>{@link Collection}（含 {@code :batchGet} 稀疏列表）→ 逐元素 mask。</li>
 * </ul>
 * 集合条目<b>逐条独立裁决</b>（{@link AccessPolicyProvider} javadoc「集合行过滤由宿主按行调用」，行级 {@code maskedFields} 可异）， 且仅处理实现
 * {@link AccessAttachable} 的条目（§19.5 法定 {@code Q*} 形态；未登记载荷不触碰——与单资源路径「record / bean 皆可脱敏」的既有语义并存）。</li>
 * <li><b>{@code ?includeAccess=true}</b>：<b>仅单资源</b>挂 {@code _access} 节点（§19.2：集合 List 端点不提供该参数，{@code _access}
 * 不随列表下发，详情页按需 拉取）——裁决 → 经 {@link AccessAttachable#withAccess} 挂载 {@link AccessNode}（载荷须实现该接口并声明
 * {@code @JsonProperty("_access")} 可空组件）。</li>
 * </ol>
 * 同一切面内<b>先 mask 后 attach</b>（节点内容取自同一份裁决结果，不出现「元数据声明已掩、报文仍有值」的窗口）；其余返回形态原样透传。
 *
 * <p>
 * <b>裁决未接入</b>：缺省 {@link PermitAllAccessPolicyProvider} → 零脱敏、零节点，行为与历史一致（向后兼容）。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 22)
public class IncludeAccessAspect {

    private static final String INCLUDE_ACCESS = "includeAccess";

    private final AccessPolicyProvider provider;

    public IncludeAccessAspect(AccessPolicyProvider provider) {
        this.provider = provider;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object body = pjp.proceed();
        if (body instanceof Etag tagged) {
            // 兼容形态：调用方已显式装配信封——脱敏内层载荷后重建信封。
            return handleEnvelope(tagged);
        }
        if (body instanceof Page<?> page) {
            return maskPage(page);
        }
        if (body instanceof Collection<?> collection) {
            return maskCollection(collection);
        }
        if (isResourcePayload(body)) {
            return maskSingle(body);
        }
        return body;
    }

    private Page<?> maskPage(Page<?> page) {
        List<AccessContext> contexts = collectMaskingContexts(page.items());
        if (contexts.isEmpty()) {
            return page;
        }
        Map<String, AccessMetadata> metadataByKey = provider.resolveBatchForMasking(contexts);
        return EnvelopeMapper.mapPage(page, resource -> maskResource(resource, metadataByKey));
    }

    private Collection<?> maskCollection(Collection<?> collection) {
        List<AccessContext> contexts = collectMaskingContexts(collection);
        if (contexts.isEmpty()) {
            return collection;
        }
        Map<String, AccessMetadata> metadataByKey = provider.resolveBatchForMasking(contexts);
        return EnvelopeMapper.mapCollection(collection, resource -> maskResource(resource, metadataByKey));
    }

    /** 单资源载荷（读模型 {@code Q*}）：脱敏恒生效；{@code ?includeAccess=true} 且载荷可挂载时追加 {@code _access} 节点（集合路径不挂节点，§19.2）。 */
    private Object maskSingle(Object resource) {
        HttpServletRequest request = currentRequest();
        boolean accessRequested = request != null && isTrue(request.getParameter(INCLUDE_ACCESS));
        AccessContext context = AccessContext.fromResourceName(resourceName(resource));
        AccessMetadata metadata = accessRequested ? provider.resolve(context) : provider.resolveWithoutActions(context);
        if (!accessRequested && metadata.maskedFields().isEmpty()) {
            return resource;
        }
        Object masked = AccessMasker.mask(resource, metadata.maskedFields());
        if (accessRequested && masked instanceof AccessAttachable<?> attachable) {
            masked = attachable.withAccess(AccessNode.from(metadata));
        }
        // 载荷未实现 AccessAttachable：无法挂节点（脱敏已完成）——接入指引见 AccessAttachable javadoc
        return masked;
    }

    /** 信封形态（兼容调用方显式装配）：脱敏内层载荷并重建信封（保留并发代数）。 */
    private Object handleEnvelope(Etag tagged) {
        Object resource = tagged instanceof Etagged<?> envelope ? envelope.resource() : tagged;
        Object masked = maskSingle(resource);
        return masked == resource ? tagged : rebuilt(tagged, masked);
    }

    /**
     * 是否为可脱敏的资源载荷：具 {@code name()} 访问器且其值为 AIP-122 规范资源名—— 含 {@code /}（{@code collection/id}），<b>或</b>载荷自声明
     * {@link AccessAttachable}（§19.5 法定 {@code Q*} 形态；AIP-156 单例无 ID 段、 相对名就是集合名本身（如 {@code
     * demoSettings}），以此入口参与 §19.2 读取脱敏与 {@code _access} 挂载）。
     *
     * <p>
     * 收窄理由：仅"有无 name() 方法"不足以判定资源载荷——业务信封也可能恰好有同名字段； 未实现 {@link AccessAttachable} 的无 {@code /} 载荷不裁决（避免误伤）。
     */
    private static boolean isResourcePayload(Object body) {
        if (body == null) {
            return false;
        }
        try {
            Object name = body.getClass().getMethod("name").invoke(body);
            if (!(name instanceof String text) || text.isBlank()) {
                return false;
            }
            return text.contains("/") || body instanceof AccessAttachable<?>;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** 收集集合条目中的 {@link AccessAttachable} 资源上下文并按规范键去重，供批量裁决使用。 */
    private static List<AccessContext> collectMaskingContexts(Iterable<?> items) {
        if (items == null) {
            return List.of();
        }
        Map<String, AccessContext> deduplicated = new LinkedHashMap<>();
        for (Object item : items) {
            Object inner = item instanceof Etagged<?> envelope ? envelope.resource() : item;
            if (inner instanceof AccessAttachable<?>) {
                AccessContext context = AccessContext.fromResourceName(resourceName(inner));
                deduplicated.putIfAbsent(AccessPolicyProvider.contextKey(context), context);
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    /** 资源级脱敏（条目与单资源共用）：仅对 §19.5 法定形态（实现 {@link AccessAttachable} 的 {@code Q*}）裁决；信封遍历由 {@link EnvelopeMapper} 承担。 */
    private Object maskResource(Object resource, Map<String, AccessMetadata> metadataByKey) {
        if (!(resource instanceof AccessAttachable<?>)) {
            return resource;
        }
        String key = AccessPolicyProvider.contextKey(AccessContext.fromResourceName(resourceName(resource)));
        AccessMetadata metadata = metadataByKey.getOrDefault(key, AccessMetadata.allowAll());
        return AccessMasker.mask(resource, metadata.maskedFields());
    }

    /** 脱敏 / 挂载后重建返回体：信封形态换载荷（保留信封携带的并发代数），直返形态要求实现 {@link AccessAttachable} 才能替换。 */
    private static Object rebuilt(Etag original, Object newResource) {
        if (original instanceof Etagged<?> envelope) {
            return new Etagged<>(newResource, envelope.lockedVersion());
        }
        return newResource;
    }

    private static String resourceName(Object resource) {
        try {
            Object name = resource.getClass().getMethod("name").invoke(resource);
            return name == null ? "" : String.valueOf(name);
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    private static boolean isTrue(String raw) {
        return raw != null && ("true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()));
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
