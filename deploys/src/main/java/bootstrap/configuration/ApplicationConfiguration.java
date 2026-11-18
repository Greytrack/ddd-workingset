package bootstrap.configuration;

import java.time.Duration;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import alm.requirement.adapters.workingset.RequirementAdapter;
import alm.requirement.infrastructure.repositories.JdbcRequirementRepository;
import sk.workingset.application.command.SourceSerialCowCoordinator;
import sk.workingset.application.command.WorkingSetItemIndexer;
import sk.workingset.application.merge.MergeComparisonService;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.repository.WorkingSetCreationSnapshotRepository;
import sk.workingset.infrastructure.repositories.JdbcBindingRepositoriesImpl;

/**
 * 组合根只保留需要运行时参数、条件装配或 lambda 的 Bean。
 *
 * <p>其余依赖关系由 {@code @Service} / {@code @Repository} / {@code @Component}
 * 与构造器注入完成。
 */
@Configuration
public class ApplicationConfiguration {

    @Bean
    public TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Bean
    public SourceSerialCowCoordinator sourceSerialCowCoordinator(
            @Value("${workingset.cow.order-gap-wait:1s}") Duration orderGapWait) {
        return new SourceSerialCowCoordinator(orderGapWait);
    }

    @Bean
    public TrackerItemCatalogQueryProvider trackerItemCatalogQueryProvider(JdbcRequirementRepository requirements) {
        return new TrackerItemCatalogQueryProvider() {
            @Override
            public java.util.List<Identity> page(TrackerId trackerId, String afterLocalId, int limit) {
                return requirements.pageActiveIdentities(trackerId, afterLocalId, limit);
            }

            @Override
            public java.util.List<Identity> identities(TrackerId trackerId) {
                return requirements.findIdentitiesByTracker(trackerId);
            }

            @Override
            public boolean hasActiveFamily(TrackerId trackerId, sk.workingset.domain.CommonId commonId) {
                return requirements.hasActiveFamily(trackerId, commonId);
            }
        };
    }

    @Bean
    public WorkingSetItemIndexer workingSetItemIndexer(
            JdbcBindingRepositoriesImpl bindings, LightTrackerItemRepository items,
            TrackerItemCatalogQueryProvider catalog, BusinessTrackerItems businessItems,
            WorkingSetCreationSnapshotRepository snapshots, JdbcRequirementRepository requirements,
            IdentifierGenerator ids, @Value("${workingset.creation.index-batch-size:100}") int batchSize) {
        return new WorkingSetItemIndexer(bindings, bindings, items, catalog, snapshots,
                businessItems, id -> new RequirementAdapter(requirements, id), ids, batchSize);
    }

    @Bean
    public BusinessTrackerItems businessTrackerItems(JdbcRequirementRepository requirements) {
        return new BusinessTrackerItems() {
            @Override
            public boolean isDeleted(LocalId localId) {
                return requirements.findById(localId).filter(item -> item.deleted()).isPresent();
            }

            @Override
            public boolean lockContentSource(LocalId localId) {
                return requirements.lockById(localId).isPresent();
            }

            @Override
            public boolean existsInTracker(TrackerId trackerId) {
                return requirements.existsInTracker(trackerId);
            }
        };
    }

    @Bean
    public MergeComparisonService mergeComparisonService(
            JdbcBindingRepositoriesImpl bindings,
            LightTrackerItemRepository items,
            TrackerItemCatalogQueryProvider catalog,
            JdbcRequirementRepository requirements,
            MergeRepository store) {
        return new MergeComparisonService(bindings, bindings, items, catalog,
                id -> new RequirementAdapter(requirements, id), store);
    }
}
