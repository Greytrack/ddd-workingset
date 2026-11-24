package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import com.airedgesoft.lib.jdbc.AggregateMapping.TableMapping;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;
import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderIndex;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.airedgesoft.lib.jdbc.JdbcWritePlan.bigint;
import static com.airedgesoft.lib.jdbc.JdbcWritePlan.text;
import static com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject.RevisionRepositorySupport.Protocol;

/**
 * v3 共表「实例级」协议行的公共装配（创建协议列 / 首版次协议列 / common 行 / rank 列值）：创建期的实例协议列与 {@code air_inst_common} 冗余行。
 *
 * <p>
 * 口径照 v3 {@code AddAction#getCommonSql}（一个实现服务所有对象类型）：{@code base_class/class/type/work_space}、
 * NoVersion 基类写空串 {@code version}、审计列文本化、{@code lock_version=0}、{@code parent_id} 兜底 0、所有者兜底创建者。
 *
 * <p>
 * <b>无业务列</b>：name / status / owner / number / tracker 这类值由各 BC 从聚合取好后放进 facts；本类只按协议落列。
 */
public final class InstanceProtocolRows {

    private InstanceProtocolRows() {
    }

    /**
     * 创建期需要的实例事实（各字段均由 BC 从聚合与常量表取好）。
     *
     * @param baseClass
     *            v3 基类 id
     * @param parentClass
     *            分类父类 id（{@code class} 列）
     * @param type
     *            分类类型 id（{@code type} 列）
     * @param workSpace
     *            产品空间
     * @param version
     *            {@code version} 列值（NoVersion 基类为空串）
     * @param workingsetId
     *            工作集
     * @param commonId
     *            common 行身份
     * @param createdBy
     *            创建人
     * @param createdTime
     *            创建时间
     * @param now
     *            本次保存时间
     */
    public record CreationFacts(long baseClass, Long parentClass, Long type, Long workSpace, String version,
            long workingsetId, long commonId, Long createdBy, long createdTime, long now) {
    }

    /**
     * 创建期的实例协议列（根行与首版次行共用同一份）。
     *
     * @param facts
     *            实例事实
     * @return 列名 → 值
     */
    public static Map<String, Value> creationValues(CreationFacts facts) {
        Map<String, Value> values = new LinkedHashMap<>();
        values.put(Protocol.BASE_CLASS, bigint(Protocol.BASE_CLASS, facts.baseClass()));
        values.put(Protocol.CLASS, bigint(Protocol.CLASS, facts.parentClass()));
        values.put(Protocol.TYPE, bigint(Protocol.TYPE, facts.type()));
        values.put(Protocol.WORK_SPACE, bigint(Protocol.WORK_SPACE, facts.workSpace()));
        values.put(Protocol.VERSION, text(Protocol.VERSION, facts.version()));
        values.put(Protocol.WORKINGSET_ID, bigint(Protocol.WORKINGSET_ID, facts.workingsetId()));
        values.put(Protocol.CREATED_BY, text(Protocol.CREATED_BY, userColumn(facts.createdBy())));
        values.put(Protocol.UPDATED_BY, text(Protocol.UPDATED_BY, userColumn(facts.createdBy())));
        values.put(Protocol.COMMON_ID, bigint(Protocol.COMMON_ID, facts.commonId()));
        values.put(Protocol.LOCK_VERSION, bigint(Protocol.LOCK_VERSION, 0L));
        values.put(Protocol.CREATED_TIME, bigint(Protocol.CREATED_TIME, facts.createdTime()));
        values.put(Protocol.UPDATED_TIME, bigint(Protocol.UPDATED_TIME, facts.now()));
        return values;
    }

    /**
     * 首版次行需要的事实（在实例事实之上叠加版次协议值）。
     *
     * @param instanceProtocolValues
     *            实例协议列（{@link #creationValues} 的产物，列值与根行同源）
     * @param rowId
     *            首版次行 id（v3：{@code revision=1} 时等于实例 id）
     * @param instanceId
     *            实例 id
     * @param instanceType
     *            实例分类类型（{@code instance_type} 列）
     * @param revisionNumber
     *            版次号
     * @param draftPhase
     *            首版是否停留签出态（决定 {@code flags}）
     * @param version
     *            {@code version} 列值
     * @param mainVersion
     *            {@code main_version} 列值
     * @param checkedInUser
     *            签出/签入操作人（v3 创建走「先签出后签入」，两列同值）
     * @param checkedInTime
     *            签出/签入时间
     */
    public record FirstRevisionFacts(Map<String, Value> instanceProtocolValues, long rowId, long instanceId,
            Long instanceType,
            int revisionNumber, boolean draftPhase, String version, String mainVersion, Long checkedInUser,
            long checkedInTime) {
    }

    /**
     * 首版次行的协议列（业务列由各 BC 的投影先行填充，协议列覆盖其上）。
     *
     * @param revisionTable
     *            版次表声明
     * @param facts
     *            首版次事实
     * @param richTextIdsByColumn
     *            富文本列名 → 内容行 id（已由基类按元数据/协议解析好；空＝无富文本）
     * @return 列名 → 值
     */
    public static Map<String, Value> firstRevisionProtocolValues(TableMapping revisionTable,
            FirstRevisionFacts facts, Map<String, Long> richTextIdsByColumn) {
        TableMapping table = revisionTable;
        Map<String, Value> values = new LinkedHashMap<>(facts.instanceProtocolValues());
        values.put(Protocol.ID, table.value(Protocol.ID, facts.rowId()));
        values.put(Protocol.INSTANCE_ID, table.value(Protocol.INSTANCE_ID, facts.instanceId()));
        values.put(Protocol.INSTANCE_TYPE, table.value(Protocol.INSTANCE_TYPE, facts.instanceType()));
        values.put(Protocol.REVISION, table.value(Protocol.REVISION, facts.revisionNumber()));
        values.put(Protocol.FLAGS, table.value(Protocol.FLAGS,
                com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionFlags
                        .created(facts.draftPhase())));
        values.put(Protocol.VERSION, table.value(Protocol.VERSION, facts.version()));
        values.put(Protocol.MAIN_VERSION, table.value(Protocol.MAIN_VERSION, facts.mainVersion()));
        values.put(Protocol.SMALL_VERSION, table.value(Protocol.SMALL_VERSION, Protocol.INITIAL_SMALL_VERSION));
        values.put(Protocol.VERSION_TYPE, table.value(Protocol.VERSION_TYPE, Protocol.VALID_VERSION_TYPE));
        values.put(Protocol.LAST_REVISION_ID, table.value(Protocol.LAST_REVISION_ID, facts.rowId()));
        values.put(Protocol.LAST_VERSION_ID, table.value(Protocol.LAST_VERSION_ID, facts.rowId()));
        values.put(Protocol.CHECKOUT_USER, table.value(Protocol.CHECKOUT_USER, facts.checkedInUser()));
        values.put(Protocol.CHECKIN_USER, table.value(Protocol.CHECKIN_USER, facts.checkedInUser()));
        values.put(Protocol.CHECKOUT_TIME, table.value(Protocol.CHECKOUT_TIME, facts.checkedInTime()));
        if (richTextIdsByColumn != null) {
            richTextIdsByColumn.forEach((column, id) -> values.put(column, table.value(column, String.valueOf(id))));
        }
        return values;
    }

    /**
     * {@code air_inst_common} 冗余行动作事实（v3 {@code getCommonSql} 口径）。
     *
     * @param id
     *            实例 id
     * @param baseClass
     *            v3 基类 id
     * @param parentClass
     *            分类父类 id
     * @param type
     *            分类类型 id
     * @param workSpace
     *            产品空间
     * @param number
     *            编号
     * @param name
     *            主属性值（v3 common.name）
     * @param status
     *            状态（v3 common.status，可回落生命周期）
     * @param version
     *            {@code version} 列值（NoVersion 基类为空串）
     * @param revision
     *            指针版次号
     * @param deleteFlag
     *            软删标记
     * @param owner
     *            所有者（缺省创建者）
     * @param commonId
     *            common 行身份
     * @param workingsetId
     *            工作集（空按默认工作集）
     * @param parentId
     *            父实例（空按 0）
     * @param tracker
     *            追踪器
     * @param createdBy
     *            创建人
     * @param createdTime
     *            创建时间
     * @param now
     *            本次保存时间
     * @param checkedOut
     *            实例签出态
     * @param maxRevision
     *            最大版次号（创建不写，可空）
     */
    public record CommonFacts(long id, long baseClass, Long parentClass, Long type, Long workSpace, String number,
            String name, Long status, String version, long revision, Integer deleteFlag, Long owner, Long commonId,
            long workingsetId, Long parentId, Long tracker, Long createdBy, long createdTime, long now,
            Integer checkedOut, Long maxRevision) {
    }

    /**
     * 组装 {@code air_inst_common} 行（交给 {@code JdbcCommonInstanceStore#save} upsert）。
     *
     * @param facts
     *            行事实
     * @return 可直接保存的 common 行
     */
    public static AirInstCommonPo commonRow(CommonFacts facts) {
        return new AirInstCommonPo(facts.id(), facts.baseClass(), facts.parentClass(), facts.type(),
                facts.workSpace(), facts.number(), facts.name(), facts.status(), facts.version(), facts.revision(),
                facts.deleteFlag(), null, userColumn(facts.createdBy()), facts.createdTime(),
                userColumn(facts.createdBy()), facts.now(), facts.checkedOut(), facts.owner(), facts.commonId(),
                facts.workingsetId(), facts.parentId() == null ? 0L : facts.parentId(), facts.tracker(),
                facts.maxRevision());
    }

    /**
     * 位置值 → v3 {@code rank} 列值：域内 {@code OrderIndex} 存的已是 v3 现网同款串（{@code bucket|rank:subRank}），
     * 直接取 {@code value()}，不做任何编码转换（DR-5：两者算法一致，转换是多余的一层）。
     *
     * @param index
     *            位置值（可空）
     * @return rank 列值；空位置返回 {@code null}
     */
    public static String rankColumnOf(OrderIndex index) {
        return index == null ? null : index.value();
    }

    /** 审计列文本化：v3 审计列是字符串列，无用户时落兼容账号（{@code RequestThreadHolder} 空用户的兜底）。 */
    private static String userColumn(Long userId) {
        return userId == null ? Protocol.NO_AUTH_ACTOR_ID : String.valueOf(userId);
    }
}
