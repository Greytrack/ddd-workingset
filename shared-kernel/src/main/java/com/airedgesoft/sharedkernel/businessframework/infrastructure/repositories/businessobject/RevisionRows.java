package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.airedgesoft.lib.jdbc.AggregateMapping.Row;
import com.airedgesoft.lib.jdbc.AggregateMapping.TableMapping;
import com.airedgesoft.lib.jdbc.JdbcWritePlan.Value;
import com.airedgesoft.lib.jdbc.StoredRow;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionNumber;

/**
 * v3 共表「指针版次」的<b>行定位与行组装</b>公共机制：版次行身份、指针行、历史基线行、比较投影的行壳。
 *
 * <p>
 * 这些口径对所有 v3 共表 BC 完全一致（{@code id}/{@code instance_id}/{@code workingset_id}/{@code revision}），
 * 与任何业务列无关，因此不留在各 BC 的 Mapper 里重复实现；<b>业务列取值仍由各 BC 声明</b>。
 */
public final class RevisionRows {

    private RevisionRows() {
    }

    /**
     * 版次行身份：首版次行 id ＝ 实例 id（v3 共表既有事实），其余取版次记录自身 id。
     *
     * @param instanceId
     *            聚合实例 id
     * @param revision
     *            版次记录
     * @return 版次行 id
     */
    public static long rowIdOf(long instanceId, RevisionItem revision) {
        return revision.getRevisionNumber() == RevisionNumber.initial().value() ? instanceId : revision.getId();
    }

    /**
     * 指针行：根行 {@code revision} 指向的版次行。
     *
     * @param children
     *            已加载的版次行
     * @param pointer
     *            指针版次号
     * @return 命中行；未命中为空（是否失败由调用方按自身错误码决定）
     */
    public static Optional<StoredRow> pointerRow(List<StoredRow> children, int pointer) {
        return children.stream()
                .filter(row -> row.integerValue(RevisionRepositorySupport.Protocol.REVISION) != null
                        && row.integerValue(RevisionRepositorySupport.Protocol.REVISION) == pointer)
                .findFirst();
    }

    /**
     * 历史基线行：版次号小于指针的最大者（取消签出的回退目标，可空）。
     *
     * @param children
     *            已加载的版次行
     * @param pointer
     *            指针版次号
     * @return 基线行；无前序版次为空
     */
    public static Optional<StoredRow> historyRow(List<StoredRow> children, int pointer) {
        return children.stream()
                .filter(row -> row.integerValue(RevisionRepositorySupport.Protocol.REVISION) != null
                        && row.integerValue(RevisionRepositorySupport.Protocol.REVISION) < pointer)
                .max(java.util.Comparator
                        .comparingInt(row -> row.integerValue(RevisionRepositorySupport.Protocol.REVISION)));
    }

    /**
     * 共享桶字段：版次行里「非版次控制」的属性值（v3 共表同列存储，按 {@code isVersioned} 回拆）。
     *
     * @param fields
     *            行解析出的全部字段（键＝落库列名）
     * @param isVersioned
     *            分桶判据（列名 → 是否版次控制）
     * @return 共享字段（可变，调用方按需追加）
     */
    public static Map<String, FieldValue> sharedFieldsOf(List<StoredRow> ignored, Map<String, FieldValue> fields,
                                                         java.util.function.Predicate<String> isVersioned) {
        Map<String, FieldValue> shared = new LinkedHashMap<>();
        fields.forEach((column, field) -> {
            if (!isVersioned.test(column)) {
                shared.put(column, field);
            }
        });
        return shared;
    }

    /**
     * 比较投影的行壳（业务列由各 BC 的 {@code JdbcRowMapping} 投影后传入）。
     *
     * @param table
     *            表声明
     * @param id
     *            行身份
     * @param instanceId
     *            实例身份（版次表写 {@code instance_id}；根表传 {@code null}）
     * @param workingsetId
     *            工作集
     * @param version
     *            锁版本
     * @param values
     *            业务列值
     * @return 可直接放进投影的行
     */
    public static Row row(TableMapping table, Long id, Long instanceId, Long workingsetId, Long version,
            Map<String, Value> values) {
        Map<String, Value> identity = new LinkedHashMap<>();
        identity.put(RevisionRepositorySupport.Protocol.ID, table.value(RevisionRepositorySupport.Protocol.ID, id));
        if (instanceId != null) {
            identity.put(RevisionRepositorySupport.Protocol.INSTANCE_ID,
                    table.value(RevisionRepositorySupport.Protocol.INSTANCE_ID, instanceId));
        }
        identity.put(RevisionRepositorySupport.Protocol.WORKINGSET_ID,
                table.value(RevisionRepositorySupport.Protocol.WORKINGSET_ID, workingsetId));
        return table.row(identity, version, values);
    }
}
