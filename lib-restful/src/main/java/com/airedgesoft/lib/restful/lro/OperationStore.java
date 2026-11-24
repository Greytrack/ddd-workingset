package com.airedgesoft.lib.restful.lro;

import com.airedgesoft.lib.restful.DomainRestCode;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * LRO 状态存储（AIP-151；v4-restful-api.md §16 异步长时运行操作模式「通用 OperationStore」）。
 *
 * <p>
 * <b>实现选择</b>：缺省 {@link InMemoryOperationStore}（单机 / 本地开发）；宿主具备 Redisson 时 {@code LroRedissonAutoConfiguration} 优先装配
 * {@link RedissonOperationStore}（多实例共享轮询状态）；BC 声明同型 Bean 即覆盖。 存储语义 = 最后写赢（{@code save} 整体覆盖），读取为终态一致的快照——Worker 推进进度无需
 * CAS；<b>终态争夺</b>（如 {@code :cancel} 与 Worker 完成）必须走 {@link #compareAndStatus}，禁止 find-then-save 裸竞争。
 *
 * <p>
 * <b>operationId 契约</b>：{@link Operation} 构造期强制 {@code operationId} 为十进制规范形态数字（无前导零、{@code long} 范围，§2 资源 ID 规则）—— 自定义
 * {@code OperationStore} / 宿主侧生成 ID 时必须满足该形态（用 {@link Operation#newId()} 即天然合规），UUID / 短码会在构造期抛
 * {@code IllegalArgumentException} （→500）；列表游标以 {@code Long} 装载主键兜底位依赖此契约。
 */
public interface OperationStore {

    /** 保存 / 覆盖 Operation（新建与进度推进、终态写入共用）。 */
    void save(Operation operation);

    /**
     * 原子 create-if-absent（触发端点 owner 兜底落库）：仅当 {@code operationId} 不存在时保存并返回 {@code true}；已存在 → <b>不写入</b>、返回
     * {@code false}（绝不覆写并发 Worker 已推进的进度/终态）。
     *
     * <p>
     * <b>缺省实现非原子</b>（find + save，多实例下仍有窗口）：单机语义足够；具备原子设施的实现（内存 {@code putIfAbsent} / Redis
     * {@code setIfAbsent}）<b>应当覆盖</b>本方法达成真原子——{@link InMemoryOperationStore} 与 {@link RedissonOperationStore} 均已覆盖。
     */
    default boolean saveIfAbsent(Operation operation) {
        if (find(operation.operationId()).isPresent()) {
            return false;
        }
        save(operation);
        return true;
    }

    /** 按 operationId 读取（不存在 → empty）。 */
    Optional<Operation> find(String operationId);

    /** 当前可见的操作列表（只读快照；{@code owner} 非空时按发起人过滤（「我的」口径），空/blank → 全量）。 */
    default List<Operation> list(String owner) {
        return List.of();
    }

    /**
     * 删除 Operation（AIP-151 {@code deleteOperation}：终态记录清理）。仅删不给、不存在亦静默——可删与不可删（进行中）的裁决由调用方（web 层）按 {@link #find}
     * 状态先行判定，本方法不做状态校验。
     *
     * <p>
     * <b>缺省实现</b>：抛 {@code DomainException(UNIMPLEMENTED)} → HTTP <b>501</b>（与 {@link #saveIfAbsent}
     * 不同，删除无法给出「尽力而为」的通用兜底： 不实现就是没有该能力）。宿主/BC 自定义实现未覆盖时以 501 显式暴露缺口，而非 500 或静默成功。
     *
     * @param operationId
     *            Operation ID
     */
    default void delete(String operationId) {
        // 不把实现类名拼进 detail（禁泄露内部实现，v4-exception-handling §2.4）；排障靠堆栈
        throw new com.airedgesoft.lib.exception.DomainException(DomainRestCode.UNSUPPORTED_BY_STORE);
    }

    /**
     * CAS 状态推进（AIP-151 取消语义的原子性锚点）：仅当当前状态等于 {@code expected} 时应用 {@code updater} 并落盘，返回更新后的 Operation； Operation
     * 不存在或状态已被并发推进（不等于 expected）→ 返回 empty，<b>不写入、不抛异常</b>——冲突翻译（409 / 幂等 200）由调用方（web 层）裁决。
     *
     * <p>
     * <b>缺省实现非原子</b>（find + 校验 + save，多实例下仍有窗口）：单机语义足够；具备原子设施的实现（内存 {@code compute} / Redis
     * {@code compareAndSet}）<b>应当覆盖</b>本方法达成真原子——{@link InMemoryOperationStore} 与 {@link RedissonOperationStore} 均已覆盖。
     *
     * @param operationId
     *            Operation ID
     * @param expected
     *            预期当前状态（如取消 = {@link OperationStatus#RUNNING}）
     * @param updater
     *            状态推进函数（纯函数，由实现方在原子区间内调用；如 {@code op -> op.withError(Operation.cancelled())}）
     * @return 推进成功 → 更新后的 Operation；不存在 / 状态不符 → {@link Optional#empty()}
     */
    default Optional<Operation> compareAndStatus(String operationId, OperationStatus expected,
            UnaryOperator<Operation> updater) {
        Optional<Operation> current = find(operationId);
        if (current.isEmpty() || OperationStatus.of(current.get()) != expected) {
            return Optional.empty();
        }
        Operation updated = updater.apply(current.get());
        save(updated);
        return Optional.of(updated);
    }

    /**
     * 终态收口（Worker 完成 / 失败 / 取消共用）：CAS {@code RUNNING → 终态} 并回读落盘后的 Operation。 不存在或已被并发推进到终态 →
     * {@link Optional#empty()}（调用方按幂等 200 / 409 裁决），<b>不覆写</b>既有终态。
     *
     * <p>
     * 缺省实现委托 {@link #compareAndStatus}（非原子兜底）；具备原子设施的实现覆盖后者即自动获得原子终态收口。
     *
     * @param operationId
     *            Operation ID
     * @param terminal
     *            终态推进函数（如 {@code op -> op.withResult(payload)} / {@code op -> op.withError(error)}）
     * @return 收口成功 → 落盘后的 Operation；不存在 / 已终态 → empty
     */
    default Optional<Operation> complete(String operationId, UnaryOperator<Operation> terminal) {
        return compareAndStatus(operationId, OperationStatus.RUNNING, terminal);
    }
}
