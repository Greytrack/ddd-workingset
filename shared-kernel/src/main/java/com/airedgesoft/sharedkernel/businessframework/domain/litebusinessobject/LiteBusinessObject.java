package com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject;

import java.util.Objects;

/**
 * 简单业务对象只读视图（值对象）：跨聚合守卫从 {@link LiteBusinessObjectRepository} 拿到的「另一个聚合」通用事实。
 *
 * <p>
 * 对应 14 需求建模契约 {@code SimpleBusinessObject}（shared-kernel 防腐层，业务对象域只读契约）。 守卫服务只依赖此只读视图，不触碰对方聚合的内部结构与状态。
 *
 * <p>
 * <b>字段口径（2026-09-23 拍板补齐通用列表列；本轮扩为 air_inst_common 全通用列）</b>：全部来自 {@code air_inst_common} 行（v3 同列）——身份与分类（id /
 * baseClass / clazz / type）、归属与结构（workSpace / commonId / workingsetId / parentId / tracker）、内容（number / name / status /
 * version / revision / maxRevision）、生命周期（deleteFlag / checkedOut / owner）、审计（createdBy / createdTime / updatedBy /
 * updatedTime）。<b>不含 lock_version</b>（写侧乐观锁，不属只读事实； 需要时由写仓储直接映射）。通用列表列与跨聚合守卫共用此最小事实。
 *
 * @param id
 *            业务对象身份
 * @param baseClass
 *            基类（分类体系一级，如需求基类）
 * @param clazz
 *            子类（分类体系叶子）
 * @param type
 *            分类 type
 * @param workSpace
 *            所属产品空间（列表「所属产品」列）
 * @param commonId
 *            公共根 id（工作集归属；默认工作集自指）
 * @param workingsetId
 *            工作集（列表「工作集」列）
 * @param parentId
 *            父对象 id（树/邻接表；空=无父）
 * @param tracker
 *            跟踪分类（工作项预置分类）
 * @param number
 *            编号
 * @param name
 *            名称（列表「描述」列）
 * @param status
 *            状态（工作流状态值；int8 大整数，非小整数）
 * @param version
 *            版本字符串（v3 口径，如 Draft.1）
 * @param revision
 *            版次号
 * @param maxRevision
 *            最大版次号
 * @param deleteFlag
 *            删除位（0=未删除，非 0=已软删；为空视为未删除——删除位口径挂账）
 * @param checkedOut
 *            签出位（1=已签出）
 * @param owner
 *            所有者
 * @param createdBy
 *            创建人（用户 id 串，渲染层转名）
 * @param createdTime
 *            创建时间（epoch 毫秒）
 * @param updatedBy
 *            修改人（用户 id 串）
 * @param updatedTime
 *            修改时间（epoch 毫秒）
 */
public record LiteBusinessObject(Long id, Long baseClass, Long clazz, Long type, Long workSpace, Long commonId,
        Long workingsetId, Long parentId, Long tracker, String number, String name, Long status, String version,
        Integer revision, Long maxRevision, Integer deleteFlag, Integer checkedOut, Long owner, String createdBy,
        Long createdTime, String updatedBy, Long updatedTime) {

    public LiteBusinessObject {
        Objects.requireNonNull(id, "id");
    }

    /** 是否已软删（deleteFlag 非空且非 0）。 */
    public boolean isDeleted() {
        return deleteFlag != null && deleteFlag != 0;
    }
}
