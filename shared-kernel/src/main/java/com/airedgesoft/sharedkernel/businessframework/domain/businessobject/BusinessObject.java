package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import java.util.Map;

/**
 * 接口：业务对象（BusinessObject）——「一类真实业务对象」的身份契约（聚合根级）。
 *
 * <p>
 * <b>定位（用户拍板 2026-09-23）</b>：继承本契约即声明「这是一个真实业务对象」（如需求聚合根 {@code Requirement}）。
 *
 * <p>
 * 泛属性的承载与读写<b>不是</b>业务对象独有——那部分能力上移到父契约 {@link BusinessDataCarrier}（数据对象抽象）： 聚合内实体（各 BC 的版次记录
 * {@code RevisionItem}）同样是业务数据承载者、同样继承父契约， 但<b>不是</b>业务对象。「是不是业务对象」（身份）与「能不能承载业务数据」（能力）由此彻底解耦；
 * 需要复用承载/读写方法的场合，依赖父契约而不是本契约。
 *
 * <p>
 * 本契约自身保留业务对象专属成员：**身份与归属**（{@link #getId()} / {@link #getType()} / {@link #getWorkSpace()} /
 * {@link #getWorkingsetId()}）＋ 删除标记；不定生命周期——生命周期归各聚合。
 */
public interface BusinessObject extends BusinessDataCarrier {

    /**
     * 未删除。
     */
    int DELETE_FLAG_NORMAL = 0;

    /**
     * 已软删（入回收站）。
     */
    int DELETE_FLAG_DELETED = 1;

    /**
     * 业务对象身份（裸 Long，稳定，不随版次变化）。
     *
     * @return 对象 id
     */
    Long getId();

    /**
     * 分类类型（分类树节点 id）。
     *
     * @return 类型 id
     */
    Long getType();

    /**
     * 所属空间（只读展示与跨空间移动口径）。
     *
     * @return 空间 id
     */
    Long getWorkSpace();

    /**
     * 所属工作集。
     *
     * @return 工作集 id
     */
    Long getWorkingsetId();

    /**
     * 公共 id（跳版次 / 跳工作集的对象身份；对应 v3 共表 {@code common_id} 与 {@code air_inst_common} 行）。
     *
     * @return 公共 id；创建期未分配时 {@code null}（由仓储按默认工作集同 id 口径补齐）
     */
    Long getCommonId();

    /**
     * 归属（对应 v3 共表 {@code tracker_id}）。
     *
     * @return 归属 id
     */
    Long getTrackerId();

    /**
     * 业务编号（对应 v3 共表 {@code number}）。
     *
     * @return 编号；装配前可空
     */
    String getNumber();

    /**
     * 父边（对应 v3 共表 {@code parent_id}）；与 {@code OrderTreeable#getParentId()} 同名同义。
     *
     * @return 父对象 id；{@code null}＝根节点
     */
    Long getParentId();

    /**
     * 创建人（对应 v3 共表 {@code created_by}）；审计列的默认操作人口径。
     *
     * @return 操作人 id；可空
     */
    Long getCreatedBy();

    /**
     * 删除标记（{@link #DELETE_FLAG_NORMAL} / {@link #DELETE_FLAG_DELETED}）。
     *
     * @return 数字标记
     */
    Integer getDeleteFlag();

    /**
     * 共享字段已生效值（三桶字段之 shared，聚合内唯一副本；只读）。
     *
     * <p>
     * 与 {@link BusinessDataCarrier#getBusinessData()}（写在本版次的内容）平级：同一列只会落在一边，
     * 分类版次控制位决定归属——写侧根行取本方法，版次行取 {@code getBusinessData()}。
     *
     * @return 己生效的共享字段（无则空 Map，不得返回 {@code null}）
     */
    Map<String, FieldValue> shared();

    /**
     * 全局唯一ID
     * @return 跨工作集唯一
     */
    default Long getLocalId(){
        return getId();
    }

}
