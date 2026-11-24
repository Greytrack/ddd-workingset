package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;

/**
 * readMask 白名单<b>自动派生</b>（Tier A 零注解）：从 Controller 方法声明的返回类型派生可选字段清单——
 * <ol>
 * <li>解包协议信封泛型：{@link Page}&lt;T&gt;（条目类型）→ {@link Etagged}&lt;T&gt;（载荷）→ {@link Optional}&lt;T&gt; /
 * {@link ResponseEntity}&lt;T&gt; / {@link Collection}&lt;T&gt;（{@code :batchGet} 稀疏列表，可组合嵌套）；</li>
 * <li>载荷为 record → 取其全部组件名（业务字段）；组件经 {@code @JsonProperty} 映射为 {@code _} 前缀协议元数据命名空间（如 {@code _access}，
 * §19.2）时<b>不入白名单</b>——元数据伴随节点不可经投影选择；</li>
 * <li>非 record（Operation 信封、String、void 等）→ 空清单：readMask 对该端点不适用，切面整体放行（不校验、不剪枝）。</li>
 * </ol>
 *
 * <p>
 * <b>为何从返回类型而非返回值派生</b>：白名单校验（400 快速失败）发生在方法体执行<b>前</b>——彼时返回值尚不存在；声明类型是唯一的白名单真相来源， 且按 {@link Method} 缓存后零重复反射。基本类型组件（如
 * {@code boolean}）入白名单（字段存在、可选择），但剪枝侧恒保留（不可置 null 供 {@code NON_NULL} 省略，见 {@link ReadMaskAspect}）。
 */
public final class ReadMaskWhitelists {

    private static final String METADATA_PREFIX = "_";

    private static final ConcurrentHashMap<Method, Set<String>> CACHE = new ConcurrentHashMap<>();

    private ReadMaskWhitelists() {
    }

    /** 方法返回类型派生的投影白名单（空清单 = readMask 不适用）。 */
    public static Set<String> of(Method method) {
        if (method == null) {
            return Set.of();
        }
        return CACHE.computeIfAbsent(method, ReadMaskWhitelists::derive);
    }

    private static Set<String> derive(Method method) {
        Class<?> payload = payloadType(method.getGenericReturnType());
        if (payload == null || !payload.isRecord() || !isReadModel(payload)) {
            return Set.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (RecordComponent component : payload.getRecordComponents()) {
            if (isProtocolMetadata(payload, component)) {
                continue;
            }
            fields.add(component.getName());
        }
        return Set.copyOf(fields);
    }

    /**
     * 是否为<b>业务读模型</b>（§1.2：读模型统一 {@code Q*} 命名，顶层 {@code Q*} 或嵌套于 {@code Q*} 的子读模型）。 只有读模型才参与 {@code
     * readMask} 投影派生——协议信封 / 回执 （{@code Operation}、{@code BatchDeleteResponse}、{@code Create*Result} 等）虽然也是
     * record，但其组件是协议骨架：把它们置 null 会破坏其规范形态 （如 {@code Operation.metadata} 为 null 直接抛 IAE → 500），且 {@code readMask}
     * 对它们没有业务语义。
     */
    private static boolean isReadModel(Class<?> payload) {
        for (Class<?> type = payload; type != null; type = type.getEnclosingClass()) {
            String simpleName = type.getSimpleName();
            if (simpleName.length() > 1 && simpleName.charAt(0) == 'Q') {
                return true; // 顶层 Q*，或嵌套在 Q* 内的子读模型（如 QPeerReview.Item）
            }
        }
        return false;
    }

    /** 方法返回类型解包后的载荷类型（信封解包至 record 条目类型；切面剪枝侧判定基本类型组件用，同包内可见）。 */
    static Class<?> payloadType(Method method) {
        return method == null ? null : payloadType(method.getGenericReturnType());
    }

    /** 解包协议信封泛型直至载荷类型（非信封 / 泛型不可解析 → 原类型；void → null）。 */
    private static Class<?> payloadType(Type type) {
        Class<?> raw = rawOf(type);
        while (type instanceof ParameterizedType parameterized && isEnvelope(raw)) {
            Type argument = parameterized.getActualTypeArguments()[0];
            if (argument instanceof WildcardType wildcard) {
                Type[] upper = wildcard.getUpperBounds();
                argument = upper.length == 0 ? Object.class : upper[0];
            }
            raw = rawOf(argument);
            if (raw == null) {
                return null;
            }
            type = argument;
        }
        return raw;
    }

    /** 信封类型判定（Page / Etagged / Optional / ResponseEntity / Collection 及其子类型（:batchGet 稀疏列表）；可继续解包）。 */
    private static boolean isEnvelope(Class<?> raw) {
        return raw == Page.class || raw == Etagged.class || raw == Optional.class || raw == ResponseEntity.class
                || (raw != null && Collection.class.isAssignableFrom(raw));
    }

    private static Class<?> rawOf(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz == void.class ? null : clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    /**
     * {@code _} 前缀协议元数据组件（{@code _access} 伴随节点等，§19.2 wire 契约）：不可经投影选择。 {@code @JsonProperty} 的 {@code @Target} 不含
     * RECORD_COMPONENT——编译器把注解安置到 backing field / 访问器方法，故三处探测取并集。
     */
    private static boolean isProtocolMetadata(Class<?> payload, RecordComponent component) {
        if (hasMetadataName(component.getAnnotation(JsonProperty.class))
                || hasMetadataName(component.getAccessor().getAnnotation(JsonProperty.class))) {
            return true;
        }
        try {
            return hasMetadataName(payload.getDeclaredField(component.getName()).getAnnotation(JsonProperty.class));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean hasMetadataName(JsonProperty property) {
        return property != null && property.value().startsWith(METADATA_PREFIX);
    }
}
