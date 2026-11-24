package com.airedgesoft.lib.restful.abac;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ABAC 裁决结果 SPI（v4-restful-api.md §19 / §19.5：裁决引擎归 IdentityAccess BC，资源 BC 只消费裁决结果——本接口即消费缝隙）。
 *
 * <p>
 * <b>接入方式</b>：BC / 宿主声明 {@link AccessPolicyProvider} Bean，按 {@link AccessContext} 返回
 * {@link AccessMetadata}（allowedActions / writableFields / valueConstraints / maskedFields）；
 * {@link IncludeAccessAspect}（读路径脱敏 + {@code _access} 节点）与 {@link AbacWriteGuard}（写路径校验链第 3 / 4 步）统一消费。
 *
 * <p>
 * <b>缺省行为（⚠ 生产警示）</b>：无任何 Provider 时 {@code AbacAutoConfiguration} 装配
 * {@link PermitAllAccessPolicyProvider}（全允许、零脱敏、零约束）—— 未接入裁决的端点行为与历史完全一致（向后兼容）。但「全允许」缺省意味着<b>生产环境漏配本 Bean 时 ABAC
 * 静默失效</b>（无报错、无日志）： 生产环境必须显式声明 {@link AccessPolicyProvider} Bean，严禁依赖缺省兜底。
 */
public interface AccessPolicyProvider {

    /**
     * 单条目裁决。
     *
     * @param context
     *            资源类型 + 资源 ID（集合 List 行过滤场景由宿主按行调用）
     */
    AccessMetadata resolve(AccessContext context);

    /**
     * 轻量单条目裁决（跳过 {@code allowedActions} 动作权限评估，供读路径脱敏与 PATCH 写字段校验使用）。
     */
    default AccessMetadata resolveWithoutActions(AccessContext context) {
        return resolve(context);
    }

    /**
     * 批量读取脱敏裁决（仅需 {@code maskedFields}，跳过 {@code allowedActions}；返回映射键见 {@link #contextKey(AccessContext)}）。
     */
    default Map<String, AccessMetadata> resolveBatchForMasking(List<AccessContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return Map.of();
        }
        Map<String, AccessMetadata> resolved = new LinkedHashMap<>();
        for (AccessContext context : contexts) {
            if (context == null) {
                continue;
            }
            resolved.computeIfAbsent(contextKey(context), ignored -> resolveWithoutActions(context));
        }
        return resolved;
    }

    /** 归一上下文缓存键：{@code resourceType/resourceId}（无 id 时为 {@code resourceType}）。 */
    static String contextKey(AccessContext context) {
        if (context == null) {
            return "";
        }
        String type = context.resourceType() == null ? "" : context.resourceType();
        String id = context.resourceId() == null ? "" : context.resourceId();
        return id.isEmpty() ? type : type + "/" + id;
    }
}
