package com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import org.jmolecules.ddd.annotation.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 聚合的命令侧存取。查询职责由 LightTrackerItemQueryProvider 承担。
 */
@Repository
public interface LightTrackerItemRepository {

    /**
     * 保存聚合（新增或更新）；内部版次成员随根一次保存。
     */
    void save(LightTrackerItem item);

    /**
     * 初次写入聚合及其版次成员。目标工作集已有同一 CommonId 时返回 false，
     * 保留原聚合。并发调用依赖持久化唯一约束判定。
     */
    boolean claimFamily(LightTrackerItem item);

    /**
     * 按 LocalId 重建聚合；读模型查询使用 {@code LightTrackerItemQueryProvider}。
     */
    Optional<LightTrackerItem> findById(LocalId localId);

    /**
     * 按 LocalId 加锁重建聚合，供并发写命令（COW 切换、软删、升版）。
     */
    Optional<LightTrackerItem> findByIdForUpdate(LocalId localId);

    /**
     * COW 命令侧领域 finder：按来源切点定位尚未物化的目标聚合。
     */
    List<LightTrackerItem> findSourceBackedTargets(LocalId sourceLocalId, Revision sourceRevision);

    /**
     * 永久移除聚合及其版次成员。
     */
    void remove(LocalId id);
}
