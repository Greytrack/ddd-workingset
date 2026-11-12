package bootstrap.configuration;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.infrastructure.JdbcRequirementRepository;
import alm.requirement.infrastructure.JdbcSnapshotStore;
import alm.requirement.infrastructure.RequirementAdapter;
import alm.requirement.infrastructure.RequirementMaterializationAdapter;
import sk.workingset.application.CowEventHandler;
import sk.workingset.application.CowFailureLog;
import sk.workingset.application.SourceSerialCowCoordinator;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.RequirementWritePort;
import sk.workingset.application.port.TargetTransactionRunner;
import sk.workingset.infrastructure.CachingLightTrackerItemRepository;
import sk.workingset.infrastructure.JdbcLightTrackerItemRepository;
import sk.workingset.infrastructure.JdbcCowFailureLog;

/**
 * 组合根（Composition Root）：唯一允许同时引用 SK 与 ALM 实现的位置（A1）。
 *
 * <p>当前装配 PostgreSQL/Spring JDBC 仓储、Adapter 工厂、雪花 ID 生成器、快照存储与 Spring Event 发布器；
 * 内存实现仅保留给单元测试，不再作为运行时 Bean。
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
            JdbcSnapshotStore snapshots, TransactionTemplate newTransactionTemplate) {
        return new RequirementMaterializationAdapter(repository, snapshots, newTransactionTemplate);
    }

    @Bean
    public CowFailureLog cowFailureLog(JdbcTemplate jdbc, TransactionTemplate newTransactionTemplate) {
        return new JdbcCowFailureLog(jdbc, newTransactionTemplate);
    }

    @Bean
    public TargetTransactionRunner targetTransactionRunner(TransactionTemplate newTransactionTemplate) {
        return new SpringTargetTransactionRunner(newTransactionTemplate);
    }

    @Bean
    public SourceSerialCowCoordinator sourceSerialCowCoordinator() {
        return new SourceSerialCowCoordinator();
    }

    @Bean
    public CowEventHandler cowEventHandler(LightTrackerItemRepository items, RequirementWritePort writePort,
            CowFailureLog failureLog, TargetTransactionRunner transactionRunner) {
        return new CowEventHandler(items, writePort, failureLog, transactionRunner);
    }

    @Bean
    public LightTrackerItemRepository lightTrackerItemRepository(JdbcTemplate jdbc,
                                                                 StringRedisTemplate redis,
                                                                 ObjectMapper objectMapper) {
        return new CachingLightTrackerItemRepository(
                new JdbcLightTrackerItemRepository(jdbc), redis, objectMapper);
    }

    @Bean
    public JdbcRequirementRepository requirementRepository(JdbcTemplate jdbc) {
        return new JdbcRequirementRepository(jdbc);
    }

    @Bean
    public JdbcSnapshotStore snapshotStore(JdbcTemplate jdbc, IdentifierGenerator identifierGenerator) {
        return new JdbcSnapshotStore(jdbc, identifierGenerator);
    }

    @Bean
    public RequirementApplicationService requirementApplicationService(
            JdbcRequirementRepository requirements,
            JdbcSnapshotStore snapshotStore,
            IdentifierGenerator identifierGenerator,
            ApplicationEventPublisher eventPublisher) {
        return new RequirementApplicationService(
                requirements, snapshotStore, identifierGenerator, eventPublisher);
    }

    @Bean
    public WorkingSetApplicationService workingSetApplicationService(
            LightTrackerItemRepository items,
            JdbcRequirementRepository requirements,
            IdentifierGenerator identifierGenerator) {
        // SK 只依赖 TrackerItemReadPort 接口；Adapter 由组合根按变体身份构造
        return new WorkingSetApplicationService(
                items,
                localId -> new RequirementAdapter(requirements, localId),
                identifierGenerator);
    }
}
