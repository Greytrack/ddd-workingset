package sk.workingset.application.port;

import java.util.List;
import java.util.Optional;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.WorkingSetId;

/** ProductSpace 聚合存取；删除前由 WorkingSetDeletionService 校验引用完整性。 */
public interface ProductSpaceRepository {
    Optional<ProductSpace> lockProductSpace(String id);
    Optional<Classification> findClassification(String id);
    List<Classification> findClassifications();
    void insertProductSpace(ProductSpace space);
    boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId);
    void deleteProductSpace(String id);
}
