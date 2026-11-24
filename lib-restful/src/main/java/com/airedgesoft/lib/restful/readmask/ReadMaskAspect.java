package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.abac.AccessMasker;
import com.airedgesoft.lib.restful.abac.EnvelopeMapper;
import com.airedgesoft.lib.restful.etag.Etag;
import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.fieldtoggle.FieldToggleProvider;
import com.airedgesoft.lib.restful.fieldtoggle.FieldToggleViolations;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.path.RequestCollections;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@code ?readMask} 字段投影切面（遵循矩阵「部分读取与字段投影」行，Tier A 框架完全接管 / AIP-157）：拦截全部 {@code @RestController}
 * 方法，业务<b>零注解零代码</b>——Controller 照常返回完整 DTO。
 * <ol>
 * <li><b>校验前置（400 快速失败）</b>：掩码解析（{@link ReadMask#parse}）+ 白名单校验（{@link ReadMaskWhitelists} 按方法返回类型自动派生，未知字段
 * {@link DomainRestCode#READ_MASK_UNKNOWN} 400 + {@code fieldViolations}）在方法体执行<b>前</b>完成；掩码显式引用停用预定义字段同样 400
 * （{@link DomainRestCode#PREDEFINED_FIELD_DISABLED}，字段不存在语义）。</li>
 * <li><b>剪枝（返回值级重建）</b>：掩码外字段 + 当前停用字段（{@code fieldtoggle} SPI 并入同一通道——掩码剪枝 ∪ 停用剔除）经 {@link AccessMasker} 规范构造器重建置
 * {@code null}，配合契约 {@code NON_NULL} 语义从报文消失；返回形态分派：单资源 / {@link Etagged} 信封（兼容显式装配形态，重建保留 {@code lockedVersion}） /
 * {@link Page}（逐条目） / {@link Collection}（逐元素，{@code :batchGet} 稀疏位不补）。</li>
 * <li><b>恒保留</b>：{@code name}（AIP-122 规范资源名，§2 保留字）与 {@code _} 前缀协议元数据组件（{@code _access}，§19.2）不受掩码影响；
 * {@code lockedVersion} 是协议信封 wire 注入字段（{@code Etagged} 包装），剪枝发生在信封装配之前，天然不受掩码影响；基本类型组件（如 {@code boolean}）不可置 null，与
 * {@code name} 一并恒保留（契约字段建议对象类型）。</li>
 * </ol>
 *
 * <p>
 * <b>ETag 一致性</b>：剪枝发生在响应装配（{@code EtagResponseAdvice} 包装 {@code lockedVersion} + 表示 hash）<b>之前</b>—— {@code ETag}
 * 恒为裁剪后最终表示的 hash；表示 hash 缓存 key 含查询串（不同掩码不同 key），304 语义不受掩码破坏。
 *
 * <p>
 * <b>顺序</b>：{@code @Order(HIGHEST_PRECEDENCE + 23)} 内层于 {@code +22} 的 ABAC 脱敏——方法返回后<b>先剪枝、ABAC 后脱敏</b>（ABAC 更靠近
 * 出口，{@code _access} 节点由其挂载）；两者都只置 null 且 {@code _} 前缀元数据不入投影白名单，先后不影响 wire 形态；整体先于全部 {@code ResponseBodyAdvice}（含 ETag
 * 装配）。
 *
 * <p>
 * <b>边界</b>：返回类型派生不出 record 白名单（Operation 信封 / String / void 等）→ readMask 与停用剔除对该端点不适用，整体放行； 请求缺席 {@code ?readMask}
 * 且无停用字段 → 原样透传（全局拦截的热路径成本 = 一次参数读取 + 一次 {@code FieldToggleProvider} 查找 + 有 Provider 时一次集合段派生）。 上下文只经请求参数与请求属性传递，
 * 无线程级状态（请求生命周期由容器管理，无跨请求串号风险）。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 23)
public class ReadMaskAspect {

    /** 停用字段若是基本类型组件则无法置 null 剪枝：按「载荷#字段」只告警一次，避免逐请求刷日志。 */
    private static final Logger log = LoggerFactory.getLogger(ReadMaskAspect.class);

    private static final Set<String> WARNED_PRIMITIVE_TOGGLES = ConcurrentHashMap.newKeySet();

    /** 投影参数名（Query 逗号式；与主规范掩码形态同源）。 */
    public static final String READ_MASK_PARAM = "readMask";

    /** 已校验的有效投影，生命周期与控制器调用一致。 */
    private static final String ACTIVE_READ_MASK = ReadMaskAspect.class.getName() + ".activeMask";

    /** 恒保留字段：AIP-122 规范资源名（§2 保留字，不受投影影响）。 */
    private static final String RESOURCE_NAME_FIELD = "name";

    private final ObjectProvider<FieldToggleProvider> toggleProvider;

    public ReadMaskAspect(ObjectProvider<FieldToggleProvider> toggleProvider) {
        this.toggleProvider = toggleProvider;
    }

    /** 返回可下推到读查询的有效字段，空集合表示当前调用没有投影限制。 */
    public static Set<String> currentPaths() {
        HttpServletRequest request = currentRequest();
        return request != null && request.getAttribute(ACTIVE_READ_MASK) instanceof ReadMask mask
                ? Set.copyOf(mask.paths()) : Set.of();
    }

    /** 在控制器调用前校验字段，使读请求可跳过无关区块。 */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return pjp.proceed();
        }
        String raw = request.getParameter(READ_MASK_PARAM);
        Set<String> disabled = disabledFields(request);
        if ((raw == null || raw.isBlank()) && disabled.isEmpty()) {
            return pjp.proceed();
        }
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Set<String> whitelist = ReadMaskWhitelists.of(method);
        if (whitelist.isEmpty()) {
            // 返回类型非资源 record：投影不适用，整体放行（不校验不剪枝）
            return pjp.proceed();
        }
        ReadMask mask = ReadMask.parse(raw);
        if (!mask.isEmpty()) {
            mask.validate(whitelist);
            // 掩码显式引用停用字段 → 400（字段不存在语义；AIP-157 掩码引用不存在字段按未知字段拒绝，遵循矩阵「预定义字段开关」行）
            FieldToggleViolations.assertPathsEnabled(mask.paths(), disabled);
        }
        Set<String> omitted = toNull(whitelist, mask, method, disabled);
        var requested = new LinkedHashSet<>(whitelist);
        requested.removeAll(omitted);
        requested.add(RESOURCE_NAME_FIELD);
        Object previous = request.getAttribute(ACTIVE_READ_MASK);
        request.setAttribute(ACTIVE_READ_MASK, new ReadMask(requested.stream().toList()));
        try {
            return prune(pjp.proceed(), omitted);
        } finally {
            if (previous == null) {
                request.removeAttribute(ACTIVE_READ_MASK);
            } else {
                request.setAttribute(ACTIVE_READ_MASK, previous);
            }
        }
    }

    /**
     * 待置 null 字段集：掩码非空 → 掩码外白名单字段 ∪ 停用字段；掩码为空（未请求投影）→ 仅停用字段。 {@code name} / 协议元数据 / 基本类型组件恒保留
     * （并集之后统一过滤——停用字段中的基本类型组件同样不可置 null）。
     */
    private static Set<String> toNull(Set<String> whitelist, ReadMask mask, Method method, Set<String> disabled) {
        Set<String> toNull = new LinkedHashSet<>(disabled);
        if (!mask.isEmpty()) {
            toNull.addAll(whitelist);
            toNull.removeAll(mask.paths());
        }
        toNull.remove(RESOURCE_NAME_FIELD);
        Class<?> payload = ReadMaskWhitelists.payloadType(method);
        Set<String> primitives = primitiveComponents(payload);
        warnIneffectivePrimitiveToggles(disabled, primitives, payload);
        toNull.removeAll(primitives);
        return toNull;
    }

    /** §20 读剔除对基本类型组件不可达（无法置 null）：按载荷只告警一次，暴露「可开关字段必须是对象类型」的契约约束。 */
    private static void warnIneffectivePrimitiveToggles(Set<String> disabled, Set<String> primitives,
            Class<?> payload) {
        if (payload == null || disabled.isEmpty() || primitives.isEmpty()) {
            return;
        }
        for (String field : disabled) {
            if (primitives.contains(field) && WARNED_PRIMITIVE_TOGGLES.add(payload.getName() + '#' + field)) {
                log.warn("停用字段 {} 在 {} 中是基本类型组件，无法置 null 剪枝（§20 读剔除对该字段不生效）：可开关字段须声明为对象类型", field, payload.getName());
            }
        }
    }

    /** 基本类型组件（不可置 null 供 NON_NULL 省略 → 恒保留）。 */
    private static Set<String> primitiveComponents(Class<?> payload) {
        if (payload == null || !payload.isRecord()) {
            return Set.of();
        }
        Set<String> primitives = new LinkedHashSet<>();
        for (RecordComponent component : payload.getRecordComponents()) {
            if (component.getType().isPrimitive()) {
                primitives.add(component.getName());
            }
        }
        return Set.copyOf(primitives);
    }

    /** 当前停用字段集合（无 {@link FieldToggleProvider} Bean → 空集 = 零开销静默放行）。 */
    private Set<String> disabledFields(HttpServletRequest request) {
        FieldToggleProvider provider = toggleProvider.getIfAvailable();
        if (provider == null) {
            return Set.of();
        }
        Set<String> disabled = provider.disabledFields(RequestCollections.of(request));
        return disabled == null ? Set.of() : disabled;
    }

    // ════════ 剪枝分派（条目级信封遍历共用 abac.EnvelopeMapper；单资源 / Optional / ResponseEntity 为本通道特有形态） ════════

    private Object prune(Object body, Set<String> toNull) {
        if (toNull.isEmpty() || body == null) {
            return body;
        }
        if (body instanceof Optional<?> optional) {
            // 解包载体验证过的 Optional / ResponseEntity 宿主形态：剪内层载荷（空值原样返回）
            return optional.map(value -> prune(value, toNull));
        }
        if (body instanceof ResponseEntity<?> entity) {
            Object value = entity.getBody();
            return value == null
                    ? entity
                    : ResponseEntity.status(entity.getStatusCode()).headers(entity.getHeaders())
                            .body(prune(value, toNull));
        }
        if (body instanceof Etag tagged) {
            // 兼容形态：调用方已显式装配信封——剪内层载荷后重建（保留并发代数）
            if (tagged instanceof Etagged<?> envelope) {
                Object pruned = AccessMasker.mask(envelope.resource(), toNull);
                return pruned == envelope.resource() ? tagged : new Etagged<>(pruned, envelope.lockedVersion());
            }
            return tagged;
        }
        if (body instanceof Page<?> page) {
            return EnvelopeMapper.mapPage(page, resource -> AccessMasker.mask(resource, toNull));
        }
        if (body instanceof Collection<?> collection) {
            return EnvelopeMapper.mapCollection(collection, resource -> AccessMasker.mask(resource, toNull));
        }
        return AccessMasker.mask(body, toNull);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
