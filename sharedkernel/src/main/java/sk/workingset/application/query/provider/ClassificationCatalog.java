package sk.workingset.application.query.provider;

import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.Classification;

import java.util.List;
import java.util.Optional;

public interface ClassificationCatalog {
    Optional<Classification> findClassification(String id);

    default Classification requireClassification(String id) {
        return findClassification(id).orElseThrow(() ->
                DomainException.withMessage(FailureReason.NOT_FOUND, "分类不存在：" + id));
    }

    List<Classification> findClassifications();
}
