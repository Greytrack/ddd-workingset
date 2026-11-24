package com.airedgesoft.lib.restful.lro;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * {@link OperationStore} 内存实现（缺省兜底：单机部署 / 本地开发 / 单测）。重启即失——生产多实例部署由 {@code RedissonOperationStore}（Redis）承载，见
 * {@code LroRedissonAutoConfiguration} 选择逻辑。
 *
 * <p>
 * {@link #compareAndStatus} 经 {@code ConcurrentHashMap#compute} 达成同键原子 CAS（同键并发修改在映射内串行化）。
 */
public class InMemoryOperationStore implements OperationStore {

    private final Map<String, Operation> operations = new ConcurrentHashMap<>();

    @Override
    public void save(Operation operation) {
        operations.put(operation.operationId(), operation);
    }

    @Override
    public boolean saveIfAbsent(Operation operation) {
        return operations.putIfAbsent(operation.operationId(), operation) == null;
    }

    @Override
    public Optional<Operation> find(String operationId) {
        return Optional.ofNullable(operations.get(operationId));
    }

    @Override
    public List<Operation> list(String owner) {
        if (owner == null || owner.isBlank()) {
            return List.copyOf(operations.values());
        }
        return operations.values().stream().filter(operation -> owner.equals(operation.owner())).toList();
    }

    @Override
    public void delete(String operationId) {
        operations.remove(operationId);
    }

    @Override
    public Optional<Operation> compareAndStatus(String operationId, OperationStatus expected,
            UnaryOperator<Operation> updater) {
        AtomicReference<Optional<Operation>> outcome = new AtomicReference<>(Optional.empty());
        operations.computeIfPresent(operationId, (id, current) -> {
            if (OperationStatus.of(current) == expected) {
                Operation updated = updater.apply(current);
                outcome.set(Optional.of(updated));
                return updated;
            }
            return current; // 状态已被并发推进 → 原样保留（不写入）
        });
        return outcome.get();
    }
}
