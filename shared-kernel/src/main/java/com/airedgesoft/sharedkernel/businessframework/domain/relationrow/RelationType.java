package com.airedgesoft.sharedkernel.businessframework.domain.relationrow;

/**
 * 枚举：关系行类型（RelationType，Shared Kernel「关系行」公共标识的取值域）。
 *
 * <p>
 * {@link RelationRowId} 由「类型 ＋ 行 id」构成，跨关系类型的清标记／看差异只认这个标识—— 各关系聚合（引用、关联、将来 BOM）把自己的 id 包成 {@code RelationRowId}
 * 时声明自身类型。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：行标识是跨关系类型定位的公共契约（10 号稿边界 5、触发口径 10）， 不属任何单一 BC；新增一种关系在此登记一个枚举值。
 */
public enum RelationType {

    /** 引用（一条引用＝一个聚合根；sourceId=引用方=下游，targetId=被引用=上游）。 */
    REFERENCE,

    /** 关联（关联行）。 */
    ASSOCIATION
}
