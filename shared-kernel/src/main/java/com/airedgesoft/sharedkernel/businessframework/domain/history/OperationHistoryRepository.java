package com.airedgesoft.sharedkernel.businessframework.domain.history;

/** 共享操作历史的追加存储。 */
public interface OperationHistoryRepository {

    /** 追加一条历史，写入失败时让调用事务回滚。 */
    void append(OperationHistory entry);
}
