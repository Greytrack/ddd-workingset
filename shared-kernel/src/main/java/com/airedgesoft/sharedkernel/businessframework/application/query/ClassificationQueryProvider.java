package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;

import java.util.List;
import java.util.Optional;

public interface ClassificationQueryProvider {
    Optional<ClassSnapshot> findClassification(Long id);

    default ClassSnapshot requireClassification(Long id) {
        return findClassification(id).orElseThrow(() -> new DomainException(FailureReason.NOT_FOUND, "分类不存在：" + id));
    }

    List<ClassSnapshot> findClassifications();
}
