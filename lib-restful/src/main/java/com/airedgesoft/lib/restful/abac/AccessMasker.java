package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.restful.updatemask.PropertyAccess;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段级脱敏（Omit）执行器（§19.2：无权字段置 {@code null} 从报文消失——{@code NON_NULL} 语义，脱敏<b>恒生效</b>不依赖 {@code includeAccess} 开关；脱敏留痕写入
 * {@code _access.maskedFields}）。
 *
 * <p>
 * <b>不可变 record 的脱敏 = 重建</b>：经规范构造器生成「被掩字段置 null / 被掩 Map 键移除」的新实例（不改原实例）； 可变 bean = setter 置 null / Map
 * 键移除。路径形态：顶层字段（{@code estimatedCostCents}）与嵌套属性路径（{@code owner.displayName}）；更深嵌套不做（脱敏语义面向契约层字段）。
 */
public final class AccessMasker {

    private AccessMasker() {
    }

    /** 按掩码集脱敏资源载荷（无掩码时原样返回）。 */
    public static Object mask(Object resource, Set<String> maskedFields) {
        if (resource == null || maskedFields == null || maskedFields.isEmpty()) {
            return resource;
        }
        if (resource instanceof Record record) {
            return maskRecord(record, maskedFields);
        }
        maskBean(resource, maskedFields);
        return resource;
    }

    private static Object maskRecord(Record record, Set<String> maskedFields) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Class<?> type = record.getClass();
        Object[] args = new Object[components.length];
        boolean changed = false;
        for (int i = 0; i < components.length; i++) {
            String name = components[i].getName();
            Object value = valueOf(record, components[i]);
            if (maskedFields.contains(name)) {
                args[i] = null;
                changed = true;
            } else if (value instanceof Map<?, ?> map && hasNestedMask(maskedFields, name)) {
                args[i] = mapWithoutKeys(map, maskedFields, name);
                changed = true;
            } else {
                args[i] = value;
            }
        }
        if (!changed) {
            return record;
        }
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            if (args[i] == null && parameterTypes[i].isPrimitive()) {
                throw new IllegalStateException(
                        "被掩字段为基本类型，无法置 null（契约字段须为对象类型）: " + type.getSimpleName() + "." + components[i].getName());
            }
        }
        try {
            Constructor<?> canonical = type.getDeclaredConstructor(parameterTypes);
            canonical.setAccessible(true);
            return canonical.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("脱敏重建 record 失败: " + type.getSimpleName(), e);
        }
    }

    private static void maskBean(Object bean, Set<String> maskedFields) {
        for (String path : maskedFields) {
            String[] segments = path.split("\\.", 2);
            if (segments.length == 1) {
                PropertyAccess.write(bean, segments[0], null);
            } else {
                Object nested = PropertyAccess.read(bean, segments[0]);
                if (nested instanceof Map<?, ?> map) {
                    map.remove(segments[1]);
                } else if (nested != null) {
                    PropertyAccess.write(nested, segments[1], null);
                }
            }
        }
    }

    private static boolean hasNestedMask(Set<String> maskedFields, String container) {
        return maskedFields.stream().anyMatch(path -> path.startsWith(container + "."));
    }

    /** 复制 Map 并移除被掩子键（不可变拷贝，不改原 Map——record 不可变语义）。 */
    private static Map<String, Object> mapWithoutKeys(Map<?, ?> map, Set<String> maskedFields, String container) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (!maskedFields.contains(container + "." + key)) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private static Object valueOf(Record record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            // 非公共类（package-private record / 嵌套于非公共类的公开 record）的访问器跨包调用需开放可达性
            if (!accessor.canAccess(record)) {
                accessor.setAccessible(true);
            }
            return accessor.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "record 访问器调用失败: " + record.getClass().getSimpleName() + "." + component.getName(), e);
        }
    }

    /** 载荷的脱敏字段探测（诊断用；返回被掩字段与其存在性的对照；载荷不存在的路径静默跳过）。 */
    public static List<String> effectiveMaskedPaths(Object resource, Set<String> maskedFields) {
        List<String> effective = new ArrayList<>();
        if (resource == null) {
            return effective;
        }
        for (String path : new HashSet<>(maskedFields)) {
            String[] segments = path.split("\\.", 2);
            try {
                Object root = PropertyAccess.read(resource, segments[0]);
                if (segments.length == 1 && root != null) {
                    effective.add(path);
                } else if (segments.length == 2 && root instanceof Map<?, ?> map && map.containsKey(segments[1])) {
                    effective.add(path);
                }
            } catch (IllegalStateException e) {
                // 诊断用途：载荷缺失字段 / 无访问器的路径静默跳过
            }
        }
        return effective;
    }
}
