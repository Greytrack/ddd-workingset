package sk.workingset.application.port;

import java.util.Optional;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;

public interface ProductSpaceRepository {
    Optional<ProductSpace> lockProductSpace(String id);
    Optional<Classification> findClassification(String id);
    void deleteProductSpace(String id);
}
