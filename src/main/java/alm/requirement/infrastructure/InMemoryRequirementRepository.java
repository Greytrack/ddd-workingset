package alm.requirement.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.domain.LocalId;

/**
 * 内存 Requirement 仓库（S1 最小持久化）。
 */
public class InMemoryRequirementRepository implements RequirementRepository {

    private final Map<LocalId, Requirement> store = new ConcurrentHashMap<>();

    @Override
    public void save(Requirement requirement) {
        store.put(requirement.localId(), requirement);
    }

    @Override
    public Optional<Requirement> findById(LocalId localId) {
        return Optional.ofNullable(store.get(localId));
    }

    /** 测试辅助：当前存储条目数。 */
    public int size() {
        return store.size();
    }
}
