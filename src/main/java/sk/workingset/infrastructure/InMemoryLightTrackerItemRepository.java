package sk.workingset.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/**
 * 内存实现（S1 最小持久化）：仅用于纵向切片验证，无数据库与事务。
 */
public class InMemoryLightTrackerItemRepository implements LightTrackerItemRepository {

    private final Map<LocalId, LightTrackerItem> store = new ConcurrentHashMap<>();

    @Override
    public void save(LightTrackerItem item) {
        store.put(item.localId(), item);
    }

    @Override
    public Optional<LightTrackerItem> findById(LocalId localId) {
        return Optional.ofNullable(store.get(localId));
    }

    @Override
    public List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision) {
        return store.values().stream()
                .filter(item -> item.sourceLocalId().equals(sourceLocalId)
                        && item.sourceRevision().equals(sourceRevision))
                .toList();
    }

    /** 测试辅助：当前存储条目数。 */
    public int size() {
        return store.size();
    }
}
