package bootstrap.configuration;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.IncompleteEventPublications;
import java.time.Duration;
import java.util.Optional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import alm.requirement.application.command.RequirementCommandService;
import alm.requirement.application.query.RequirementQueryService;
import alm.requirement.application.query.provider.RequirementQueryProvider;
import alm.requirement.application.command.RequirementLifecycleService;
import alm.requirement.application.command.RequirementMergeWriter;
import alm.requirement.infrastructure.providers.RequirementQueryProviderImpl;
import alm.requirement.infrastructure.providers.RequirementQueryMapper;
import alm.requirement.infrastructure.repositories.JdbcRequirementRepository;
import alm.requirement.infrastructure.repositories.JdbcSnapshotRepository;
import alm.requirement.adapters.workingset.RequirementAdapter;
import alm.requirement.adapters.workingset.RequirementMaterializationAdapter;
import sk.workingset.application.command.CowEventHandler;
import sk.workingset.application.CowFailureLog;
import sk.workingset.domain.repository.CowMaterializationRepository;
import sk.workingset.application.command.CowRecoveryService;
import sk.workingset.application.MergeComparisonService;
import sk.workingset.application.command.MergeExecutionService;
import sk.workingset.application.command.MergeRelationRepairStep;
import sk.workingset.application.command.RelationEventPublisher;
import sk.workingset.application.command.RelationEventStep;
import sk.workingset.application.command.TrackerItemLifecycleService;
import sk.workingset.application.port.RelationSourcePort;
import sk.workingset.domain.merge.MergeRepository;
import sk.workingset.infrastructure.repositories.JdbcMergeRepository;
import sk.workingset.domain.event.RequirementChanged;
import sk.workingset.domain.event.RequirementMergeApplied;
import sk.workingset.domain.event.RequirementPurged;
import sk.workingset.application.command.SourceSerialCowCoordinator;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.command.WorkingSetCreationService;
import sk.workingset.application.command.WorkingSetDeletionService;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.infrastructure.providers.WorkingSetQueryProviderImpl;
import sk.workingset.infrastructure.providers.mybatis.WorkingSetQueryMapper;
import sk.workingset.application.command.WorkingSetItemIndexer;
import sk.workingset.application.port.TrackerItemCatalog;
import sk.workingset.domain.repository.WorkingSetCreationSnapshotRepository;
import sk.workingset.domain.repository.WorkingSetCreationRepository;
import sk.workingset.infrastructure.EmptyRelationSourcePort;
import sk.workingset.infrastructure.JdbcWorkingSetCreationSnapshotRepository;
import sk.workingset.infrastructure.repositories.JdbcWorkingSetCreationRepository;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.application.port.TrackerScopePort;
import sk.workingset.infrastructure.repositories.CachingLightTrackerItemRepository;
import sk.workingset.infrastructure.repositories.JdbcBindingRepositories;
import sk.workingset.infrastructure.repositories.JdbcLightTrackerItemRepository;
import sk.workingset.infrastructure.JdbcCowFailureLog;
import sk.workingset.infrastructure.repositories.JdbcCowMaterializationRepository;

/**
 * 组合根（Composition Root）：唯一允许同时引用 SK 与 ALM 实现的位置（A1）。
 *
 * <p>当前装配 PostgreSQL/Spring JDBC 仓储、Adapter 工厂、雪花 ID 生成器、快照存储与 Spring Event 发布器；
 * 持久化验收使用同一套 JDBC 装配。
 */
@Configuration
public class ApplicationConfiguration {

    @Bean
    public TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    @Bean
    public RequirementWritePort requirementWritePort(JdbcRequirementRepository repository,
                                                     JdbcSnapshotRepository snapshots, RequirementCommandService requirements) {
        return new RequirementMaterializationAdapter(repository, snapshots,
                new RequirementMergeWriter(repository, requirements));
    }

    @Bean
    public CowFailureLog cowFailureLog(JdbcTemplate jdbc, TransactionTemplate newTransactionTemplate,
                                      IdentifierGenerator ids) {
        return new JdbcCowFailureLog(jdbc, newTransactionTemplate, ids);
    }

    @Bean
    public SourceSerialCowCoordinator sourceSerialCowCoordinator(
            @Value("${workingset.cow.order-gap-wait:1s}") Duration orderGapWait) {
        return new SourceSerialCowCoordinator(orderGapWait);
    }

    @Bean
    public CowMaterializationRepository cowMaterializationStore(JdbcTemplate jdbc) {
        return new JdbcCowMaterializationRepository(jdbc);
    }

    @Bean
    public CowRecoveryService cowRecoveryService(CowMaterializationRepository store, LightTrackerItemRepository items,
                                                 RequirementWritePort writePort, CowFailureLog failureLog,
                                                 TransactionTemplate newTransactionTemplate,
                                                 IncompleteEventPublications publications) {
        return new CowRecoveryService(store, items, writePort, failureLog, newTransactionTemplate,
                () -> publications.resubmitIncompletePublications(p ->
                        p.getEvent() instanceof RequirementChanged
                                || p.getEvent() instanceof RequirementPurged
                                || p.getEvent() instanceof RequirementMergeApplied));
    }

    @Bean
    public CowEventHandler cowEventHandler(LightTrackerItemRepository items, TrackerItemLifecyclePort lifecycle,
                                           RequirementWritePort writePort,
                                           CowFailureLog failureLog, TransactionTemplate newTransactionTemplate,
                                           CowMaterializationRepository store, CowRecoveryService recovery) {
        return new CowEventHandler(items, lifecycle, writePort, failureLog, newTransactionTemplate, store, recovery);
    }

    @Bean
    public LightTrackerItemRepository lightTrackerItemRepository(JdbcTemplate jdbc,
                                                                 StringRedisTemplate redis,
                                                                 ObjectMapper objectMapper,
                                                                 @Value("${workingset.item-cache.enabled:true}") boolean cacheEnabled) {
        var repository = new JdbcLightTrackerItemRepository(jdbc);
        return cacheEnabled ? new CachingLightTrackerItemRepository(repository, redis, objectMapper) : repository;
    }

    @Bean
    public JdbcRequirementRepository requirementRepository(JdbcTemplate jdbc) {
        return new JdbcRequirementRepository(jdbc);
    }

    @Bean
    public JdbcSnapshotRepository snapshotStore(JdbcTemplate jdbc, IdentifierGenerator identifierGenerator) {
        return new JdbcSnapshotRepository(jdbc, identifierGenerator);
    }

    @Bean
    public RequirementCommandService requirementCommandService(
            JdbcRequirementRepository requirements,
            JdbcSnapshotRepository snapshotStore,
            IdentifierGenerator identifierGenerator,
            ApplicationEventPublisher eventPublisher,
            TrackerScopePort trackerScopePort,
            TrackerItemLifecyclePort lifecycle) {
        return new RequirementCommandService(
                requirements, snapshotStore, identifierGenerator, eventPublisher, trackerScopePort, lifecycle);
    }

    @Bean
    public RequirementQueryProvider RequirementQueryProvider(RequirementQueryMapper mapper) {
        return new RequirementQueryProviderImpl(mapper);
    }

    @Bean
    public RequirementQueryService requirementQueryService(RequirementQueryProvider query,
            JdbcRequirementRepository repository, TrackerItemLifecyclePort lifecycle,
            TrackerScopePort trackerScopePort) {
        return new RequirementQueryService(query, repository, lifecycle, trackerScopePort);
    }

    @Bean
    public TrackerScopePort trackerScopePort(JdbcBindingRepositories trackers) {
        return new TrackerScopePort() {
            @Override
            public Optional<TrackerScope> find(TrackerId trackerId) {
                return trackers.findTracker(trackerId).map(tracker -> {
                    requireWorkingSetReady(tracker.workingSetId());
                    return new TrackerScope(tracker.workingSetId(), tracker.sharingMode());
                });
            }

            @Override
            public void requireWorkingSetReady(WorkingSetId workingSetId) {
                trackers.requireReady(workingSetId);
            }
        };
    }

    @Bean
    public JdbcBindingRepositories bindingRepositories(JdbcTemplate jdbc) {
        return new JdbcBindingRepositories(jdbc);
    }

    @Bean
    public WorkingSetCreationRepository WorkingSetCreationRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new JdbcWorkingSetCreationRepository(jdbc, mapper);
    }

    @Bean
    public WorkingSetCreationSnapshotRepository workingSetCreationSnapshotStore(JdbcTemplate jdbc) {
        return new JdbcWorkingSetCreationSnapshotRepository(jdbc);
    }

    @Bean
    public RelationSourcePort relationSourcePort() {
        // 业务 BC 关系事实 Adapter 接入前保持空实现；不能伪造关系事件。
        return new EmptyRelationSourcePort();
    }

    @Bean
    public RelationEventPublisher relationEventPublisher(ApplicationEventPublisher events,
                                                         IdentifierGenerator ids) {
        return new RelationEventPublisher(events, ids);
    }

    @Bean
    public RelationEventStep relationEventStep(JdbcBindingRepositories bindings,
                                                LightTrackerItemRepository items,
                                                RelationSourcePort source,
                                                RelationEventPublisher publisher) {
        return new RelationEventStep(bindings, bindings, items, source, publisher);
    }

    @Bean
    public WorkingSetCreationService workingSetCreationService(WorkingSetCreationRepository store,
            JdbcBindingRepositories bindings, WorkingSetCommandService workingSets,
            TransactionTemplate newTransactionTemplate, ApplicationEventPublisher events, WorkingSetItemIndexer indexer,
            RelationEventStep relationEventStep, IdentifierGenerator ids) {
        return new WorkingSetCreationService(store, bindings, bindings, workingSets, newTransactionTemplate, events,
                indexer, relationEventStep, ids);
    }

    @Bean
    public TrackerItemCatalog trackerItemCatalog(JdbcRequirementRepository requirements) {
        return new TrackerItemCatalog() {
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
    public WorkingSetItemIndexer workingSetItemIndexer(JdbcBindingRepositories bindings,
                                                       LightTrackerItemRepository items, TrackerItemCatalog catalog,
                                                       WorkingSetCreationSnapshotRepository snapshots, BusinessTrackerItems businessItems,
                                                       JdbcRequirementRepository requirements, IdentifierGenerator ids,
                                                       @Value("${workingset.creation.index-batch-size:100}") int batchSize) {
        return new WorkingSetItemIndexer(bindings, bindings, items, catalog, snapshots, businessItems,
                id -> new RequirementAdapter(requirements, id), ids, batchSize);
    }

    @Bean
    public RequirementLifecycleService requirementLifecycleService(JdbcRequirementRepository requirements,
            TrackerItemLifecyclePort lifecycle, ApplicationEventPublisher events) {
        return new RequirementLifecycleService(requirements, lifecycle, events);
    }

    @Bean
    public TrackerItemLifecyclePort trackerItemLifecyclePort(JdbcBindingRepositories trackers,
            LightTrackerItemRepository items) {
        return new TrackerItemLifecycleService(trackers, items);
    }

    @Bean
    public BusinessTrackerItems businessTrackerItems(JdbcRequirementRepository requirements) {
        return new BusinessTrackerItems() {
            @Override
            public Optional<SourceIdentity> findIdentity(LocalId localId) {
                return requirements.findById(localId).filter(item -> !item.deleted())
                        .map(item -> new SourceIdentity(item.trackerId(), item.commonId()));
            }

            @Override
            public boolean isDeleted(LocalId localId) {
                return requirements.findById(localId).filter(item -> item.deleted()).isPresent();
            }

            @Override
            public boolean lockContentSource(LocalId localId) {
                return requirements.lockById(localId).isPresent();
            }

            @Override
            public Optional<Presentation> presentation(LocalId localId) {
                return requirements.findPresentation(localId);
            }

            @Override
            public boolean existsInTracker(TrackerId trackerId) {
                return requirements.existsInTracker(trackerId);
            }
        };
    }

    @Bean
    public MergeRepository MergeRepository(JdbcTemplate jdbc, ObjectMapper mapper, IdentifierGenerator ids) {
        return new JdbcMergeRepository(jdbc, mapper, ids);
    }

    @Bean
    public MergeComparisonService mergeComparisonService(JdbcBindingRepositories bindings,
            LightTrackerItemRepository items, TrackerItemCatalog catalog,
            JdbcRequirementRepository requirements, MergeRepository store) {
        return new MergeComparisonService(bindings, bindings, items, catalog,
                id -> new RequirementAdapter(requirements, id), store);
    }

    @Bean
    public MergeRelationRepairStep mergeRelationRepairStep(RelationSourcePort source,
                                                           RelationEventPublisher publisher) {
        return new MergeRelationRepairStep(source, publisher);
    }

    @Bean
    public MergeExecutionService mergeExecutionService(MergeRepository store, MergeComparisonService comparisons,
            RequirementWritePort writer, JdbcBindingRepositories trackers, IdentifierGenerator ids,
            TransactionTemplate newTransactionTemplate, ApplicationEventPublisher events,
            MergeRelationRepairStep relationRepair) {
        return new MergeExecutionService(store, comparisons, writer, trackers, ids, newTransactionTemplate, events,
                relationRepair);
    }

    @Bean
    public WorkingSetDeletionService workingSetDeletionService(JdbcBindingRepositories bindings,
            LightTrackerItemRepository items, BusinessTrackerItems businessItems) {
        return new WorkingSetDeletionService(bindings, bindings, bindings, items, businessItems);
    }

    @Bean
    public WorkingSetCommandService workingSetCommandService(
            IdentifierGenerator identifierGenerator,
            JdbcBindingRepositories bindings) {
        return new WorkingSetCommandService(identifierGenerator, bindings, bindings, bindings);
    }

    @Bean
    public WorkingSetQueryProvider WorkingSetQueryProvider(WorkingSetQueryMapper mapper) {
        return new WorkingSetQueryProviderImpl(mapper);
    }

    @Bean
    public WorkingSetQueryService workingSetQueryService(
            WorkingSetQueryProvider query,
            LightTrackerItemRepository items,
            JdbcRequirementRepository requirements,
            BusinessTrackerItems businessItems) {
        // SK 只依赖 TrackerItemReadPort 接口；Adapter 由组合根按变体身份构造
        return new WorkingSetQueryService(query, items,
                localId -> new RequirementAdapter(requirements, localId), businessItems);
    }
}
