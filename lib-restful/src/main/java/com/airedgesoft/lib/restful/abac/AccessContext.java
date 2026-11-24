package com.airedgesoft.lib.restful.abac;

import java.util.Map;

/**
 * ABAC 裁决请求上下文（资源类型 + 资源 ID；操作者身份由认证层解析——严禁信任前端传入身份，规范 §19.5）。
 *
 * @param resourceType
 *            资源类型（URI 首段复数资源名，如 {@code demoTasks}）
 * @param resourceId
 *            契约 ID（资源名 {@code name} 尾段，纯数字字符串）
 */
public record AccessContext(String resourceType, String resourceId) {

    /** 从资源相对名 {@code demoTasks/623809242866016} 派生上下文（无 {@code /} 视为无 ID 的类型级裁决）。 */
    public static AccessContext fromResourceName(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            return new AccessContext("", "");
        }
        int slash = resourceName.lastIndexOf('/');
        if (slash < 0) {
            return new AccessContext(resourceName, "");
        }
        return new AccessContext(resourceName.substring(0, slash), resourceName.substring(slash + 1));
    }

    /** 操作者附加属性（宿主按需携带；本库不解释）。 */
    public Map<String, Object> attributes() {
        return Map.of();
    }
}
