package com.airedgesoft.sharedkernel.businessframework.domain.association;

/**
 * 枚举：关系行两端（EndSide）。
 *
 * <p>
 * 端语义固定（关联稿触发口径 5）：{@code SOURCE}＝源=下游、{@code TARGET}＝目标=上游。 规则（v3 {@code drive_type}）以「触发端 / 结果端」表达，落值对象
 * {@link AssociationRule}。
 */
public enum EndSide {

    /** 源端（下游；v3 {@code instance_id} 侧）。 */
    SOURCE,

    /** 目标端（上游；v3 {@code related_instance_id} 侧）。 */
    TARGET
}
