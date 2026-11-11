package alm.requirement.infrastructure;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import sk.workingset.application.event.SnapshotRef;
import sk.workingset.application.port.IdentifierGenerator;

/**
 * 内存快照存储（S2）：引用 ID 由雪花生成器产生；保存后内容不可变。
 */
public class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, RequirementSnapshot> store = new ConcurrentHashMap<>();
    private final IdentifierGenerator identifierGenerator;

    public InMemorySnapshotStore(IdentifierGenerator identifierGenerator) {
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
    }

    @Override
    public SnapshotRef save(RequirementSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        SnapshotRef ref = SnapshotRef.of(String.valueOf(identifierGenerator.nextId()));
        store.put(ref.id(), snapshot);
        return ref;
    }

    @Override
    public Optional<RequirementSnapshot> find(SnapshotRef ref) {
        Objects.requireNonNull(ref, "ref");
        return Optional.ofNullable(store.get(ref.id()));
    }

    /** 测试辅助：当前快照条数。 */
    public int size() {
        return store.size();
    }
}
