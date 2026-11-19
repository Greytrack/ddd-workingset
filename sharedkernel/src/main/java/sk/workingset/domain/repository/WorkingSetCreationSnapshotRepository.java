package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

import java.util.List;

/**
 * 创建任务受理时冻结的父 Item 身份分页。
 *
 * <p>受理事务写入快照后，索引阶段只读取该快照；终态后不补扫。快照中的来源是受理时点
 * 的有效内容来源，执行时若来源缺失、软删或改版，由索引记录跳过明细。
 */
@Repository
public interface WorkingSetCreationSnapshotRepository {

    /**
     * 写入或补齐一个创建任务的快照；空集合也会写入完成标记。
     */
    void save(String creationId, List<Item> items);

    List<Item> page(String creationId, String parentTrackerId, String afterLocalId, int limit);

    boolean contains(String creationId);

    record Item(String parentTrackerId, LocalId localId, CommonId commonId, Revision revision,
                LocalId sourceLocalId, Revision sourceRevision) {
    }
}
