package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.domain.Classification;

import java.util.List;
import java.util.Optional;

public interface ClassificationQueryProvider {
    Optional<Classification> findClassification(String id);

    default Classification requireClassification(String id) {
        return findClassification(id).orElseThrow(() ->
                DomainException.withMessage(FailureReason.NOT_FOUND, "分类不存在：" + id));
    }

    List<Classification> findClassifications();
}
