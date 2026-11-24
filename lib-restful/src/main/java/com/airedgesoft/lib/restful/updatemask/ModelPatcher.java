package com.airedgesoft.lib.restful.updatemask;

/**
 * {@code updateMask} 属性覆盖器（v4-restful-api.md §3 PATCH 行 / AIP-161；Tier B「声明式注解驱动」反射工具）。
 *
 * <p>
 * {@link #apply(Object, Object, UpdateMask)}：以请求体 record（source）为值源，仅对掩码声明的字段路径逐项读取并写入聚合实体 / PO（target）——
 * <b>掩码外字段不受影响</b>（§3）；掩码内显式 {@code null} = 清空该字段（setter 赋 {@code null} / Map 键移除语义交由目标类型）； 嵌套属性以点号路径直达目标（如
 * {@code owner.displayName}）。
 *
 * <p>
 * <b>职责边界</b>：本类只做「已通过校验的掩码 → 属性覆盖」，不做白名单校验（{@link UpdateMask#validate} / {@link ValidUpdateMask} 承担 400）、
 * 不做权限校验（{@code abac.AbacWriteGuard} 承担 403）。掩码路径无法在 target 导航（嵌套层为 null）→ 快速失败 {@code IllegalStateException}（配置错误不静默）。
 *
 * <pre>
 * // adapters 层典型拼装（demo 接入示例）：
 * UpdateMask mask = UpdateMaskAspect.currentMask(); // 或手工 UpdateMask.parse(request.getParameter("updateMask"))
 * DemoTask aggregate = repository.byId(command.taskId()); // 已完成 lockedVersion 并发断言
 * ModelPatcher.apply(request.body(), aggregate, mask, Set.of("title", "description")); // 仅掩码字段覆盖
 * </pre>
 */
public final class ModelPatcher {

    private ModelPatcher() {
    }

    /**
     * 把 source 中掩码声明的字段值覆盖到 target（AIP-134 模式 + AIP-161 output-only 忽略）。
     *
     * <p>
     * ALL_POPULATED / FULL_REPLACE 的候选字段限定在 {@code writable} 内，output-only 字段不在白名单即被忽略；EXPLICIT 路径建议已过白名单校验。
     * {@code writable} 为 null / 空时<b>候选集为空</b>（fail-closed，不回退枚举请求体属性）——省略 / {@code *} 掩码必须显式传白名单， 否则「全写」语义静默退化为「不写」。
     *
     * @param source
     *            值源（请求体 record / bean；读访问器按 record 组件或 getX/isX 解析）
     * @param target
     *            覆盖目标（聚合实体 / PO；写访问器 {@code setX(value)}，Map 段按键写入）
     * @param mask
     *            已解析的更新掩码（任意 {@link UpdateMask.Mode}；内部经 {@link UpdateMask#resolve} 归一为具体路径）
     * @param writable
     *            可写字段白名单（null / 空 → 无可写字段，fail-closed）
     * @return target（便于链式）
     */
    public static <T> T apply(Object source, T target, UpdateMask mask, java.util.Set<String> writable) {
        UpdateMask effective = mask.resolve(source, writable);
        for (String path : effective.paths()) {
            PropertyAccess.write(target, path, PropertyAccess.read(source, path));
        }
        return target;
    }
}
