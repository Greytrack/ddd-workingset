package sk.workingset.application.query.provider;

import sk.workingset.domain.Classification;

import java.util.List;
import java.util.Optional;

public interface ClassificationCatalog {
    Optional<Classification> findClassification(String id);

    List<Classification> findClassifications();
}
