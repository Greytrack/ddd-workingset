package com.airedgesoft.lib.restful.etag;

import java.lang.reflect.Method;

/**
 * 载荷资源名读取（AIP-122 相对资源名 {@code collection/{id}}，规范 §2）。
 *
 * <p>
 * <b>是规范资源名，不是人读名称</b>：AIP-122 的 {@code name} 是客户端据以构造路径的规范标识（恒为 {@code collection/{id}} 形态）；资源的人读名称另有字段
 * （{@code displayName} / {@code title}，AIP-148），不占用 {@code name}（§2 保留字）。
 *
 * <p>
 * 读模型 {@code Q*} 以 {@code name()} 暴露资源名；本工具以反射读取该访问器，缺失 / 非 String / 形态不符 （不含 {@code /}）→ 视为「非资源载荷」（业务信封 / 错误响应等不参与
 * ETag 装配）。
 */
final class ResourceName {

    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Method> ACCESSORS = new java.util.concurrent.ConcurrentHashMap<>();

    private ResourceName() {
    }

    /** 载荷的资源名（非资源载荷 → null）。 */
    static String of(Object payload) {
        if (payload == null) {
            return null;
        }
        Method accessor = ACCESSORS.computeIfAbsent(payload.getClass(), ResourceName::findAccessor);
        if (accessor == null) {
            return null;
        }
        try {
            Object value = accessor.invoke(payload);
            if (!(value instanceof String name) || !name.contains("/")) {
                return null;
            }
            return name;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method findAccessor(Class<?> type) {
        try {
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals("name")
                        && method.getReturnType() == String.class) {
                    return method;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }
}
