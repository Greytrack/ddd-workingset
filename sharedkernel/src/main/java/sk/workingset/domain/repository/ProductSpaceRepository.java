package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * ProductSpace 聚合存取；删除前由 WorkingSetDeletionService 校验引用完整性。
 */
@Repository
public interface ProductSpaceRepository {
    Optional<ProductSpace> lockProductSpace(String id);

    Optional<Classification> findClassification(String id);

    List<Classification> findClassifications();

    void insertProductSpace(ProductSpace space);

    boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId);

    void deleteProductSpace(String id);
}
