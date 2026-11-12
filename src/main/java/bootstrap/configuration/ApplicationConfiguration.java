package bootstrap.configuration;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.infrastructure.JdbcRequirementRepository;
import alm.requirement.infrastructure.JdbcSnapshotStore;
import alm.requirement.infrastructure.RequirementAdapter;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.infrastructure.CachingLightTrackerItemRepository;
import sk.workingset.infrastructure.JdbcLightTrackerItemRepository;

/**
 * 组合根（Composition Root）：唯一允许同时引用 SK 与 ALM 实现的位置（A1）。
 *
 * <p>当前装配 PostgreSQL/Spring JDBC 仓储、Adapter 工厂、雪花 ID 生成器、快照存储与 Spring Event 发布器；
 * 内存实现仅保留给单元测试，不再作为运行时 Bean。
 */
@Configuration
public class ApplicationConfiguration {

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
