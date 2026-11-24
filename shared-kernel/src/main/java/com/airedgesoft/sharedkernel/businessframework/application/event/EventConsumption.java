package com.airedgesoft.sharedkernel.businessframework.application.event;

/** 让消费确认随当前事务中的业务写入一起提交。 */
public interface EventConsumption {

    /** 锁定未确认记录并执行写入，失败时保留记录供重投。 */
    void consume(String id, Runnable action);
}
