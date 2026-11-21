package bootstrap.configuration;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.command.SourceSerialCowCoordinator;
import sk.workingset.application.command.WorkingSetItemIndexer;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.application.query.provider.BusinessObjectTypeRegistry;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.domain.workingset.WorkingSetCreationSnapshotStore;
import sk.workingset.domain.workingset.WorkingSetRepository;

import java.time.Duration;

/**
 * 组合根只保留需要运行时参数、条件装配或 lambda 的 Bean。
 *
 * <p>组合根不依赖任何业务 BC 的 Adapter 或基础设施；各 BC 通过
 * {@code BusinessObjectTypeAdapter} 由 Spring 自动发现，SK 内部由
 * {@code BusinessObjectTypeRegistry} 按 Tracker 分类路由。
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
    public WorkingSetItemIndexer workingSetItemIndexer(
            WorkingSetRepository bindings,
            TrackerRepository trackers,
            LightTrackerItemQueryProvider itemQueries,
            LightTrackerItemRepository items,
            WorkingSetCreationSnapshotStore snapshots,
            BusinessObjectTypeRegistry registry,
            IdentifierGenerator ids,
            @Value("${workingset.creation.index-batch-size:100}") int batchSize) {
        return new WorkingSetItemIndexer(bindings, trackers, itemQueries, items, snapshots,
                registry, ids, batchSize);
    }
}
