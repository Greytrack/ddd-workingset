package com.airedgesoft.lib.restful.etag;

/**
 * 条件读取头归一与并发代数换算工具（v4 共享工具，lib-restful）——<b>不负责生成 {@code ETag}</b>。
 *
 * <p>
 * <b>职责边界（决策 C4 / C5）</b>：{@code ETag} 恒为响应表示的 hash（由 {@link EtagResponseAdvice} 计算），<b>不再</b>由并发代数派生； 并发控制只依赖 body
 * {@code lockedVersion}。本类仅保留两类与协议头解析无关的能力：
 * <ul>
 * <li>{@link #normalize}：{@code If-None-Match} 条件读取头的归一（去空白、弱校验器前缀、首尾引号）；</li>
 * <li>{@link #generationOf} / {@link #storageVersionOf}：对外 {@code lockedVersion} 与存储 {@code @Version} 的具名换算。</li>
 * </ul>
 *
 * <p>
 * {@code If-Match} 与逐条令牌形态已随「并发只依赖 body {@code lockedVersion}」的裁决整体移除——写请求不再读取任何版本请求头。
 */
public final class EtagSupport {

    private EtagSupport() {
    }

    /**
     * 归一化条件请求头（{@code If-None-Match}）：去空白、去弱校验器前缀 {@code W/}、去<b>首尾</b>双引号；null / 空白 → null。
     *
     * <p>
     * 只剥首尾引号（不做全局替换）——token 中间的引号属非法形态，由比较方按不等处理。
     */
    public static String normalize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String normalized = header.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized.isBlank() ? null : normalized;
    }

    /**
     * 存储版本 → 对外版本代数（{@code lockedVersion}）：N = 存储 {@code @Version} + 1；{@code null}（尚未持久化 / 新建落库即 0）→ 1。
     */
    public static Long generationOf(Long storageVersion) {
        return storageVersion == null ? 1L : storageVersion + 1L;
    }

    /**
     * 对外版本代数 → 存储版本（{@link #generationOf} 的逆运算）：存储 {@code @Version} = N - 1。 代数换算的<b>具名唯一落点</b>——规范 §6 禁止各处手写
     * {@code ±1}（请求体逐项携带代数的批量路径经本函数折算期望存储版本）。
     */
    public static Long storageVersionOf(Long generation) {
        return generation == null ? null : generation - 1L;
    }
}
