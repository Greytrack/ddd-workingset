package com.airedgesoft.lib.restful.id;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;

/**
 * 技术主键在 <b>HTTP 契约（String）↔ 领域 / 持久化（Long）</b> 之间的统一转换工具（规范 v4-restful-api §2）。
 *
 * <p>
 * 契约层（路径变量、Query、Command / Query / Result / {@code Q*} 字段）一律 String（十进制纯数字，对齐 AIP-122 资源名）； 领域层、应用内部与持久化可继续使用
 * Long。转换只发生在两处：入参在 adapters 解析路径变量 / 在应用服务消费 Command 字段时调用 {@link #parse}；出参在应用服务产出 Result / {@code Q*} 时调用
 * {@link #format}。禁止各 BC 自写 {@code Long.valueOf}。
 */
public final class ResourceIds {

    private ResourceIds() {
    }

    /**
     * 契约 String → Long。
     *
     * <p>
     * 只接受<b>规范形态</b>：ASCII {@code [0-9]}、无前导零（单个 {@code "0"} 除外）——{@link Character#isDigit} / {@link Long#parseLong}
     * 会放行全角等 Unicode 数字与前导零，导致同一资源出现多个合法 ID 串（违背 AIP-122 资源名唯一）。
     *
     * @throws DomainException
     *             空串 / 非 ASCII 十进制纯数字 / 前导零 / 超出 long 范围 → 400（{@link DomainRestCode#RESOURCE_ID_INVALID}）
     */
    public static long parse(String id) {
        if (!isCanonical(id)) {
            throw new DomainException(DomainRestCode.RESOURCE_ID_INVALID, id);
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new DomainException(DomainRestCode.RESOURCE_ID_INVALID, e, id);
        }
    }

    private static boolean isCanonical(String id) {
        if (id == null || id.isEmpty() || (id.length() > 1 && id.charAt(0) == '0')) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /** 可空入参的解析：null → null，其余同 {@link #parse}。 */
    public static Long parseNullable(String id) {
        return id == null ? null : parse(id);
    }

    /**
     * AIP-122 相对资源名 → 末端 id（如 {@code "productSpaces/623809242866016"} → 623809242866016）。
     *
     * @param resourceName
     *            相对资源名（{@code {collection}/{id}} 形态）
     * @param collection
     *            集合段（如 {@code productSpaces}）
     * @throws com.airedgesoft.lib.exception.DomainException
     *             资源名为空 / 集合段不符 / 末端 id 非规范形态 → 400（{@link DomainRestCode#RESOURCE_ID_INVALID}）
     */
    public static long parseName(String resourceName, String collection) {
        String prefix = collection + "/";
        if (resourceName == null || !resourceName.startsWith(prefix)) {
            throw new DomainException(DomainRestCode.RESOURCE_ID_INVALID, resourceName);
        }
        return parse(resourceName.substring(prefix.length()));
    }

    /** 可空资源名解析：null → null，其余同 {@link #parseName}。 */
    public static Long parseNullableName(String resourceName, String collection) {
        return resourceName == null ? null : parseName(resourceName, collection);
    }

    /** Long → 契约 String；null → null。 */
    public static String format(Long id) {
        return id == null ? null : Long.toString(id);
    }
}
