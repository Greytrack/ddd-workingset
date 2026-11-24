package com.airedgesoft.lib.restful.updatemask;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PATCH updateMask 的解析结果（Query 逗号式 {@code ?updateMask=title,description}；AIP-134 / AIP-161）。
 *
 * <p>
 * <b>AIP-134 掩码语义（{@link Mode}）</b>：
 * <ul>
 * <li><b>省略 / 未传</b>（{@code raw == null}）→ {@link Mode#ALL_POPULATED}：更新请求体中<b>所有已填充（非
 * null）</b>的可写字段（AIP-134:104）；</li>
 * <li><b>显式空串</b>（{@code ""} / 空白）→ {@link Mode#NO_OP}：不更新任何字段（AIP-134:107-109）；</li>
 * <li><b>{@code "*"}</b> → {@link Mode#FULL_REPLACE}：全量替换所有可写字段（掩码内显式 {@code null} = 清空，AIP-134:110）；</li>
 * <li><b>逗号分隔路径</b> → {@link Mode#EXPLICIT}：仅声明字段参与变更，掩码内显式 {@code null} = 清空。</li>
 * </ul>
 *
 * <p>
 * <b>解析规则</b>：逗号分隔 → 逐段 trim → 空段（连续逗号）报 400 {@link DomainRestCode#UPDATE_MASK_INVALID}；去重保序
 * （{@code LinkedHashSet}）；点号路径原样保留（如 {@code customFields.trackingCode} 寻址）。白名单校验见 {@link #validate(Set)}（未知字段 400 + 逐字段
 * {@code fieldViolations}）。
 *
 * <p>
 * <b>模式 → 具体路径</b>：{@link #resolve(Object, Set)} 把 ALL_POPULATED / FULL_REPLACE / NO_OP 归一为 EXPLICIT 具体路径清单 （供下游
 * {@code List<String>} 契约消费）；候选字段<b>只</b>取自可写白名单（{@code writable}），白名单缺省即候选集为空（fail-closed，不回退枚举请求体属性）。output-only
 * 字段不在白名单内即被忽略（AIP-161:86-94）。
 */
public record UpdateMask(Mode mode, List<String> paths) {

    /** AIP-134 掩码模式。 */
    public enum Mode {
        /** 省略掩码：更新所有已填充（非 null）可写字段。 */
        ALL_POPULATED,
        /** {@code *}：全量替换所有可写字段（null = 清空）。 */
        FULL_REPLACE,
        /** 显式逗号路径：仅声明字段参与变更。 */
        EXPLICIT,
        /** 显式空串：不更新任何字段。 */
        NO_OP
    }

    public UpdateMask {
        paths = List.copyOf(paths);
    }

    /** 显式路径掩码（下游 {@code List<String>} 契约 / 手工链常用）。 */
    public UpdateMask(List<String> paths) {
        this(Mode.EXPLICIT, paths);
    }

    /**
     * 解析 Query 逗号式掩码（AIP-134）：{@code null} → ALL_POPULATED；空白 → NO_OP；{@code "*"} → FULL_REPLACE；其余 → EXPLICIT（含空段 →
     * 400 UPDATE_MASK_INVALID）。
     */
    public static UpdateMask parse(String raw) {
        if (raw == null) {
            return new UpdateMask(Mode.ALL_POPULATED, List.of());
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new UpdateMask(Mode.NO_OP, List.of());
        }
        if (trimmed.equals("*")) {
            return new UpdateMask(Mode.FULL_REPLACE, List.of());
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String segment : trimmed.split(",")) {
            String path = segment.trim();
            if (path.isEmpty()) {
                throw new DomainException(DomainRestCode.UPDATE_MASK_INVALID);
            }
            paths.add(path);
        }
        return new UpdateMask(Mode.EXPLICIT, List.copyOf(paths));
    }

    /**
     * 把模式归一为 EXPLICIT 具体路径清单（AIP-134）：
     * <ul>
     * <li>EXPLICIT → 原样（路径已过白名单）；NO_OP → 空清单；</li>
     * <li>FULL_REPLACE → 全部候选可写字段；ALL_POPULATED → 候选中请求体值非 null 的字段。</li>
     * </ul>
     * 候选字段<b>只</b>取 {@code writable}（白名单，天然排除 output-only）：白名单为 null / 空即「无可写字段」（fail-closed）， 绝不回退枚举请求体属性——否则省略掩码 +
     * 空白名单会把 {@code *}/省略掩码放大成「全写」，令 §19.3 静态白名单形同不存在。
     *
     * @param source
     *            请求体（值源；ALL_POPULATED 据此判定「已填充」）
     * @param writable
     *            可写字段白名单（null / 空 → 无可写字段，机器化语义 fail-closed）
     */
    public UpdateMask resolve(Object source, Set<String> writable) {
        return switch (mode) {
            case EXPLICIT -> this;
            case NO_OP -> new UpdateMask(Mode.EXPLICIT, List.of());
            case FULL_REPLACE -> new UpdateMask(Mode.EXPLICIT, candidatePaths(writable));
            case ALL_POPULATED -> new UpdateMask(Mode.EXPLICIT, candidatePaths(writable).stream()
                    .filter(path -> PropertyAccess.read(source, path) != null).toList());
        };
    }

    private static List<String> candidatePaths(Set<String> writable) {
        return writable == null || writable.isEmpty() ? List.of() : new ArrayList<>(writable);
    }

    /** 白名单校验：未知字段 → 400 {@link DomainRestCode#UPDATE_MASK_UNKNOWN} + 逐字段 {@code fieldViolations}（一次返回全部，规范 §8）。 */
    public void validate(Set<String> whitelist) {
        List<String> unknown = paths.stream().filter(path -> !whitelist.contains(path)).toList();
        if (unknown.isEmpty()) {
            return;
        }
        DomainException ex = new DomainException(DomainRestCode.UPDATE_MASK_UNKNOWN, String.join(", ", unknown));
        throw ex.withFieldViolations(unknown.stream().map(
                path -> com.airedgesoft.lib.exception.FieldViolation.of(path, DomainRestCode.UPDATE_MASK_UNKNOWN, path))
                .toList());
    }

    /** 掩码是否声明某字段路径。 */
    public boolean includes(String path) {
        return paths.contains(path);
    }

    /** 掩码字段数。 */
    public int size() {
        return paths.size();
    }
}
