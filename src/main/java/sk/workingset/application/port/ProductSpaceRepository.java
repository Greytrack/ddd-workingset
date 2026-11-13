package sk.workingset.application.port;

import java.util.Optional;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;

/** ProductSpace 聚合存取；删除前由 WorkingSetDeletionService 校验引用完整性。 */
public interface ProductSpaceRepository {
    Optional<ProductSpace> lockProductSpace(String id);
    Optional<Classification> findClassification(String id);
    void deleteProductSpace(String id);
}
