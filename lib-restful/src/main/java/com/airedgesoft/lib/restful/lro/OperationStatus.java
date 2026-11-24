package com.airedgesoft.lib.restful.lro;

/**
 * LRO Operation 生命周期状态分类（AIP-151；从 {@link Operation} wire 形态派生的离散状态——CAS 状态推进 {@link OperationStore#compareAndStatus}
 * 的预期态锚点）。
 *
 * <p>
 * 派生规则：{@code done=false} → {@link #RUNNING}；{@code done=true} 且无 error → {@link #SUCCEEDED}； error.reason = 取消语义
 * （{@link Operation#cancelled()}）→ {@link #CANCELLED}；其余 error → {@link #FAILED}。
 */
public enum OperationStatus {

    /** 进行中（未达终态，可取消）。 */
    RUNNING,
    /** 成功终态。 */
    SUCCEEDED,
    /** 失败终态（非用户取消）。 */
    FAILED,
    /** 用户取消终态。 */
    CANCELLED;

    /** 由 Operation wire 形态派生状态（分类唯一落点，禁止各处重复手写 done/error 判定链）。 */
    public static OperationStatus of(Operation operation) {
        if (!operation.done()) {
            return RUNNING;
        }
        if (operation.error() == null) {
            return SUCCEEDED;
        }
        return Operation.cancelled().reason().equals(operation.error().reason()) ? CANCELLED : FAILED;
    }
}
