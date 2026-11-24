package com.airedgesoft.sharedkernel.businessframework.application.command.history;

import com.airedgesoft.sharedkernel.businessframework.application.event.EventConsumption;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 接收后台投递的共享历史写命令。 */
@Service
@RequiredArgsConstructor
public class OperationHistoryCommandService {

    private final EventConsumption consumption;
    private final OperationHistoryRepository histories;

    /** 在同一事务中校验并追加一个条目，重投沿用同一条消费记录。 */
    @EventListener
    @Transactional
    public void record(RecordOperationHistory command) {
        consumption.consume(command.origin().deliveryId(), () ->
                histories.append(new OperationHistory(command.origin(), command.subject(), command.actor(),
                        command.review())));
    }
}
