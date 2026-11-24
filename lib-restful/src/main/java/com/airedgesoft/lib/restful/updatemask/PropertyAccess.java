package com.airedgesoft.lib.restful.updatemask;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * 点号路径上的 JavaBean / record 属性读写（{@link ModelPatcher} 与 {@code abac.AbacWriteGuard} 值读取共用）。
 *
 * <p>
 * <b>读访问器</b>（source，通常为请求体 record）：{@code segment()}（record 组件）／{@code getSegment()}／boolean {@code isSegment()}； Map
 * 段按键取值；<b>写访问器</b>（target，通常为聚合实体 / PO）：{@code setSegment(value)}——多重载时选形参类型与值兼容的第一个。 全部反射按方法名精确匹配，不做字段直写（保持封装）。
 */
public final class PropertyAccess {

    private PropertyAccess() {
    }

    /** 按点号路径逐段读取；中途 null（嵌套属性缺失）→ null。Map 段按键取值。 */
    public static Object read(Object source, String dotPath) {
        Object current = source;
        for (String segment : dotPath.split("\\.")) {
            if (current == null) {
                return null;
            }
            if (current instanceof java.util.Map<?, ?> map) {
                current = map.get(segment);
                continue;
            }
            current = invoke(current, reader(current.getClass(), segment),
                    current.getClass().getSimpleName() + "." + segment);
        }
        return current;
    }

    /**
     * 按点号路径写入：末段经 setter 赋值（值可为 null = 清空）；父段为 Map 时按键写入； bean 中间段沿 getter 导航到既有嵌套对象（嵌套对象缺失 →
     * 快速失败——掩码声明了白名单路径而目标结构不存在属配置错误，不静默创建）。
     */
    public static void write(Object target, String dotPath, Object value) {
        List<String> segments = Arrays.asList(dotPath.split("\\."));
        Object current = target;
        for (String segment : segments.subList(0, segments.size() - 1)) {
            Object next;
            if (current instanceof java.util.Map<?, ?> map) {
                next = map.get(segment);
            } else {
                next = invoke(current, reader(current.getClass(), segment),
                        target.getClass().getSimpleName() + "." + segment);
            }
            if (next == null) {
                throw new IllegalStateException(
                        "ModelPatcher 嵌套路径导航失败（目标对象缺该层实例）: " + dotPath + " @ " + target.getClass().getSimpleName());
            }
            current = next;
        }
        String leaf = segments.get(segments.size() - 1);
        if (current instanceof java.util.Map<?, ?> map) {
            putEntry(map, leaf, value);
            return;
        }
        Method setter = writer(current.getClass(), leaf, value == null ? null : value.getClass());
        try {
            ensureAccessible(setter, current);
            setter.invoke(current, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("属性写入失败: " + target.getClass().getSimpleName() + "." + leaf + "(set)", e);
        }
    }

    /** Map 键写入（捕获通配；value 可为 null = 清空语义交由目标类型）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void putEntry(java.util.Map<?, ?> map, String key, Object value) {
        ((java.util.Map) map).put(key, value);
    }

    private static Object invoke(Object bean, Method method, String what) {
        try {
            ensureAccessible(method, bean);
            return method.invoke(bean);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("属性访问失败: " + what, e);
        }
    }

    /** 非公共类（package-private record / 嵌套类型）的访问器跨包调用需开放可达性。 */
    private static void ensureAccessible(Method method, Object bean) throws ReflectiveOperationException {
        if (!method.canAccess(bean)) {
            method.setAccessible(true);
        }
    }

    /** 读访问器：record 组件（同名）→ getX → is（boolean）。 */
    private static Method reader(Class<?> type, String segment) {
        Method exact = findMethod(type, segment, 0);
        if (exact != null && exact.getParameterCount() == 0 && exact.getReturnType() != void.class) {
            return exact;
        }
        Method getter = findMethod(type, "get" + capitalize(segment), 0);
        if (getter != null) {
            return getter;
        }
        Method isser = findMethod(type, "is" + capitalize(segment), 0);
        if (isser != null) {
            return isser;
        }
        throw new IllegalStateException("缺少读访问器: " + type.getSimpleName() + "." + segment);
    }

    /** 写访问器：setSegment(value)；value 类型给定时优先形参可赋值的重载。 */
    private static Method writer(Class<?> type, String segment, Class<?> valueType) {
        String name = "set" + capitalize(segment);
        Method match = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1
                    || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (valueType == null || method.getParameterTypes()[0].isAssignableFrom(valueType)) {
                match = method;
                break;
            }
        }
        if (match == null) {
            throw new IllegalStateException("缺少写访问器: " + type.getSimpleName() + ".set" + capitalize(segment));
        }
        return match;
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount
                    && !Modifier.isStatic(method.getModifiers())) {
                return method;
            }
        }
        return null;
    }

    private static String capitalize(String segment) {
        return Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
    }
}
