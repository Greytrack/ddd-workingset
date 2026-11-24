package com.airedgesoft.sharedkernel.businessframework.domain.relationrow;

import java.util.Objects;

/**
 * 值对象：关系行标识（RelationRowId，Shared Kernel「关系行」公共契约）。
 *
 * <p>
 * 跨关系类型的清标记／看差异只认这个标识（10 号稿边界 5、触发口径 10）：{@code relationType ＋ rowId}， 由各关系聚合把自身 id
 * 包成它（{@code Reference#relationRowId()} 内部 {@code RelationRowId.of(RelationType.REFERENCE, referenceId)}），转换只在聚合内发生——本
 * BC 不拥有具体关系 id 类型。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：传播怀疑的公共链路（父仓储、清标记服务）按此标识定位行， 不认识具体关系聚合；裸 Long 二义性（v3「先查引用表再查关联表」）由此显式化消除。
 *
 * @param relationType
 *            关系行类型（非空）
 * @param rowId
 *            关系行 id（非空；各聚合自身的主键）
 */
public record RelationRowId(RelationType relationType, Long rowId) {

    public RelationRowId {
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(rowId, "rowId");
    }

    /**
     * 构造关系行标识。
     *
     * @param relationType
     *            关系行类型
     * @param rowId
     *            关系行 id
     * @return 标识实例
     */
    public static RelationRowId of(RelationType relationType, Long rowId) {
        return new RelationRowId(relationType, rowId);
    }
}
