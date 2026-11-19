package sk.workingset.application.query.provider;

import java.util.List;
import java.util.Optional;

import sk.workingset.domain.Classification;

public interface ClassificationCatalog {
    Optional<Classification> findClassification(String id);

    List<Classification> findClassifications();
}
