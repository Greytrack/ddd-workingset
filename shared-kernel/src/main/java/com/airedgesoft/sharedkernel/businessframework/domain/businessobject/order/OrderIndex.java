package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.math.BigDecimal;

/**
 * 值对象：位置值（shared-kernel）。
 *
 * <p>
 * 位置值的编码 / 比较 / 初值三件知识收敛于此；编排放值由 shared-kernel {@code OrderPolicy} 批量计算，本类只承载单值语义与比较口径。
 */
public record OrderIndex(String value) implements Comparable<OrderIndex> {

    public OrderIndex {
        if (value == null || value.isBlank()) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.ORDER_INDEX_INVALID,
                    value);
        }
        // 位置值恒为 v3 lexorank 串：历史数字位置（如 "128"）在此归一化，库内不再出现纯数字 rank
        if (value.indexOf('|') < 0) {
            value = lexoRankAt(value);
        }
    }

    /** 数字位置 → v3 lexorank 串（第 n 位）：v3 现网同一套 {@code LexoRank.min().genNext()} 推进链。 */
    private static String lexoRankAt(String numeric) {
        int position;
        try {
            position = new BigDecimal(numeric.trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return com.github.pravin.raha.lexorank4j.LexoRank.min().genNext().format();
        }
        com.github.pravin.raha.lexorank4j.LexoRank rank = com.github.pravin.raha.lexorank4j.LexoRank.min().genNext();
        for (int step = 1; step < position; step++) {
            rank = rank.genNext();
        }
        return rank.format();
    }

    /** 初值（容器首个位置值）。 */
    public static OrderIndex initial() {
        return new OrderIndex(com.github.pravin.raha.lexorank4j.LexoRank.min().genNext().format());
    }

    /**
     * 线上初值：v3 现网 lexorank 首值（{@code LexoRank.min().genNext()} = {@code 0|100000:}）。
     *
     * <p>
     * 只要容器内已有 lexorank 位置值，新增成员必须沿链推进（{@link #next()}），不能回到本初值， 否则会与既有成员撞值/乱序——这是保留历史数据后与 v3 同库共写的前提。
     */
    public static OrderIndex lexoInitial() {
        return new OrderIndex(com.github.pravin.raha.lexorank4j.LexoRank.min().genNext().format());
    }

    /** 是否 v3 lexorank 形态（{@code bucket|rank:subRank}）。 */
    public boolean isLexoRank() {
        return value.indexOf('|') >= 0;
    }

    /** 后继位置值：lexorank 沿链推进（与 v3 {@code DefaultRank#nextValue} 同口径），数值形态则 +1。 */
    public OrderIndex next() {
        return new OrderIndex(com.github.pravin.raha.lexorank4j.LexoRank.parse(value).genNext().format());
    }

    /** 前驱位置值：lexorank 沿链回退（与 v3 {@code DefaultRank#prev} 同口径），数值形态则 -1。 */
    public OrderIndex prev() {
        return new OrderIndex(com.github.pravin.raha.lexorank4j.LexoRank.parse(value).genPrev().format());
    }

    /** 指定值。 */
    public static OrderIndex of(String value) {
        return new OrderIndex(value);
    }

    /** 比较口径：可数值化者按数值比较，否则按字典序（同值由调用方按 id 兜底）。 */
    @Override
    public int compareTo(OrderIndex other) {
        BigDecimal left = numericOrNull(value);
        BigDecimal right = numericOrNull(other.value);
        if (left != null && right != null) {
            return left.compareTo(right);
        }
        return value.compareTo(other.value);
    }

    /**
     * 取 {@code this} 与 {@code after} 之间的位置值。
     *
     * <p>
     * lexorank 形态走 {@code LexoRank#between}（与 v3 {@code DefaultRank#betweenValue} 同口径，两侧须不同值）； 数值形态取中点（存量数值位值的防御性口径）。
     * 单边界 / 空边界的裁决由 {@code OrderPolicy} 负责，不在本原语内兜底。
     *
     * @param after
     *            右邻居位置值；不可为 {@code null}，且须与 {@code this} 同形态
     */
    public OrderIndex between(OrderIndex after) {
        if (isLexoRank() && after != null && after.isLexoRank()) {
            return new OrderIndex(com.github.pravin.raha.lexorank4j.LexoRank.parse(value)
                    .between(com.github.pravin.raha.lexorank4j.LexoRank.parse(after.value)).format());
        }
        BigDecimal left = numericOrNull(value);
        BigDecimal right = after == null ? null : numericOrNull(after.value);
        if (left == null || right == null) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.ORDER_INDEX_INVALID,
                    "位置值非数值或形态不一致，无法取中");
        }
        return new OrderIndex(left.add(right).divide(BigDecimal.valueOf(2)).stripTrailingZeros().toPlainString());
    }

    private static BigDecimal numericOrNull(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
