package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.exception.FieldViolation;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code ?readMask} 字段投影的解析结果（遵循矩阵「部分读取与字段投影」行 / AIP-157 部分读取；Query 逗号式 {@code ?readMask=title,status}）。
 *
 * <p>
 * <b>解析规则</b>：参数<b>可选</b>（缺席 / 空白 → 空掩码 = 不投影，区别于 {@code updateMask} 省略=更新全部已填充字段——AIP-157 掩码缺席即不投影）； 逗号分隔 → 逐段 trim →
 * 空段跳过（连续逗号宽容处理）→ 去重保序（{@link LinkedHashSet}）。
 *
 * <p>
 * <b>白名单校验</b>见 {@link #validate(Set)}：未知字段 → 400 {@link DomainRestCode#READ_MASK_UNKNOWN} + 逐字段
 * {@code fieldViolations}（一次返回全部，规范 §8）——防拼写错误被静默吞掉后全量返回。
 */
public record ReadMask(List<String> paths) {

    public ReadMask {
        paths = List.copyOf(paths);
    }

    /** 解析 Query 逗号式投影掩码；{@code raw} 缺席 / 空白 → 空掩码（不投影）。 */
    public static ReadMask parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ReadMask(List.of());
        }
        Set<String> paths = new LinkedHashSet<>();
        for (String segment : raw.split(",")) {
            String path = segment.trim();
            if (path.isEmpty()) {
                continue;
            }
            paths.add(path);
        }
        return new ReadMask(List.copyOf(paths));
    }

    /** 白名单校验：未知字段 → 400 {@link DomainRestCode#READ_MASK_UNKNOWN} + 逐字段 {@code fieldViolations}。 */
    public void validate(Set<String> whitelist) {
        List<String> unknown = paths.stream().filter(path -> !whitelist.contains(path)).toList();
        if (unknown.isEmpty()) {
            return;
        }
        throw new DomainException(DomainRestCode.READ_MASK_UNKNOWN, String.join(", ", unknown)).withFieldViolations(
                unknown.stream().map(path -> FieldViolation.of(path, DomainRestCode.READ_MASK_UNKNOWN, path)).toList());
    }

    /** 是否为空掩码（不投影）。 */
    public boolean isEmpty() {
        return paths.isEmpty();
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
