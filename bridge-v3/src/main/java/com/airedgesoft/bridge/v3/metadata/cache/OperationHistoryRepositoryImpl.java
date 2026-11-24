package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistoryRepository;
import org.springframework.stereotype.Component;

@Component
public class OperationHistoryRepositoryImpl implements OperationHistoryRepository {
    @Override
    public void append(OperationHistory entry) {

    }
}
