package com.airedgesoft.lib.restful.abac;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.FieldViolation;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.updatemask.PropertyAccess;
import com.airedgesoft.lib.restful.updatemask.UpdateMask;
import java.util.ArrayList;
import java.util.List;

/**
 * ABAC 写路径校验组件（§19.3 四步校验链的第 3 步「字段级写权限」与第 4 步「值级约束」；403 + 逐条 {@code fieldViolations}，一次返回全部）。
 *
 * <p>
 * <b>四步链拼装方式（demo 接入指引，adapters 层 PATCH 端点按序调用）</b>：
 * <ol>
 * <li>掩码语法 + 静态白名单 400 —— {@code @ValidUpdateMask} / {@code UpdateMask.parse(...).validate(...)}；</li>
 * <li>版本并发断言 409 —— 基础设施按请求级 {@code EtagContext} 在加载处比对聚合版本（body {@code lockedVersion} 必填，缺失 400；由
 * {@code PreconditionAspect} 校验并绑定；删除 / 恢复族豁免）；</li>
 * <li><b>字段级写权限 403 —— 本类 {@link #guard}（writableFields）</b>；</li>
 * <li><b>值级约束 403 —— 本类 {@link #guard}（valueConstraints）</b>。</li>
 * </ol>
 * 注意分工边界：掩码白名单 400 在第 1 步（拼写防御），值掩码白名单 403 在本类（权限语义），版本冲突 409 在第 2 步——三码不混用。
 *
 * <p>
 * <b>空集合语义</b>：{@link AccessMetadata#allowAll()}（{@code enforceWritable=false} 且 {@code writableFields} 为空）=
 * 不启用可写白名单（缺省 Provider 向后兼容）；{@link AccessMetadata#restricted} / {@link AccessMetadata#denyAll}
 * （{@code enforceWritable=true}）即使 {@code writableFields} 为空也按 fail-closed 拒绝所有字段写入（不在集合 →
 * 403）。{@code valueConstraints} 按路径精确匹配，目标值不在候选集 → 403（显式 null 清空不受值约束限制——清空语义由业务校验裁决）。
 */
public final class AbacWriteGuard {

    private AbacWriteGuard() {
    }

    /**
     * 校验掩码内字段路径及其目标值（source 为请求体 record，值经 {@code PropertyAccess.read} 点号路径读取）。
     *
     * @throws DomainException
     *             403 + {@code fieldViolations}（FIELD_NOT_WRITABLE / VALUE_CONSTRAINT_VIOLATED 逐条）
     */
    public static void guard(AccessMetadata metadata, UpdateMask mask, Object source) {
        boolean writableRestricted = metadata.enforceWritable() || !metadata.writableFields().isEmpty();
        List<FieldViolation> violations = new ArrayList<>();
        List<String> unwritable = new ArrayList<>();
        List<String> constrained = new ArrayList<>();
        for (String path : mask.paths()) {
            if (writableRestricted && !metadata.writableFields().contains(path)) {
                unwritable.add(path);
                violations.add(FieldViolation.of(path, DomainRestCode.FIELD_NOT_WRITABLE, path));
                continue;
            }
            var allowed = metadata.valueConstraints().get(path);
            if (allowed == null || allowed.isEmpty()) {
                continue;
            }
            Object value = PropertyAccess.read(source, path);
            if (value != null && !allowed.contains(String.valueOf(value))) {
                constrained.add(path);
                violations.add(
                        FieldViolation.of(path, DomainRestCode.VALUE_CONSTRAINT_VIOLATED, path, String.valueOf(value)));
            }
        }
        if (violations.isEmpty()) {
            return;
        }
        DomainException exception = new DomainException(DomainErrorType.FORBIDDEN,
                !unwritable.isEmpty() ? DomainRestCode.FIELD_NOT_WRITABLE : DomainRestCode.VALUE_CONSTRAINT_VIOLATED,
                String.join(", ", !unwritable.isEmpty() ? unwritable : constrained));
        throw exception.withFieldViolations(violations);
    }
}
