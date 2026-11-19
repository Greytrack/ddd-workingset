package bootstrap;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.jdbc.core.JdbcTemplate;

import alm.requirement.application.command.RequirementCommandService;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * Spring Boot 上下文与事件发布检查（S2/S3：上下文可加载、事件发布；Modulith 在提交后异步消费）。
 */
class ApplicationContextTest {

    private static final Revision R3 = Revision.of(3);

    @Test
    void context_loads_without_web_and_exposes_composition_root_beans() {
        try (ConfigurableApplicationContext context = start()) {
            assertNotNull(context.getBean(WorkingSetCommandService.class));
            assertNotNull(context.getBean(WorkingSetQueryService.class));
            assertNotNull(context.getBean(RequirementCommandService.class));
            assertNotNull(context.getBean(IdentifierGenerator.class));
        }
    }

    @Test
    void requirement_changed_is_published_and_visible_to_synchronous_listeners() {
        try (ConfigurableApplicationContext context = start()) {
            List<BusinessObjectUpdateEvent> received = new CopyOnWriteArrayList<>();
            context.addApplicationListener(
                    (ApplicationListener<PayloadApplicationEvent<?>>) event -> {
                        if (event.getPayload() instanceof BusinessObjectUpdateEvent changed) received.add(changed);
                    });
            RequirementCommandService service = context.getBean(RequirementCommandService.class);
            TrackerId trackerId = TrackerId.of(fixtureId("tracker-" + UUID.randomUUID()));

            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            RootTrackerFixture fixture = new RootTrackerFixture(jdbc, trackerId.value());
            fixture.provision();
            LocalId created = null;
            try {
                alm.requirement.domain.Requirement requirement = service.create(trackerId, WorkingSetId.of("1"), "A", "source description", R3);
                created = requirement.localId();
                await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM event_publication WHERE event_type LIKE '%.TrackerItemCreatedEvent'
                        AND serialized_event LIKE ? AND completion_date IS NOT NULL)
                        """, Boolean.class, "%" + requirement.localId().value() + "%")));
                assertEquals(created.value(), requirement.commonId().value(),
                        "默认工作集创建时 CommonId 与 LocalId 必须相等");
                verifyPublishedChange(service, received, created);
            } finally {
                if (created != null) {
                    fixture.removeRequirement(created.value());
                }
                fixture.remove();
            }
        }
    }

    private void verifyPublishedChange(RequirementCommandService service, List<BusinessObjectUpdateEvent> received,
                                       LocalId localId) {
        service.changeTitle(localId, "B", LockVersion.of(0));

        assertEquals(1, received.size(), "来源事件应在发布线程内对同步监听器可见");
        BusinessObjectUpdateEvent event = received.getFirst();
        assertEquals(localId, event.requirementId());
        assertEquals(localId, event.sourceLocalId());
        assertEquals(R3, event.beforeRevision());
        assertEquals(R3, event.afterRevision());
        assertEquals(0L, event.beforeLockVersion().value());
        assertEquals(1L, event.afterLockVersion().value());
        assertTrue(String.valueOf(event.eventId()).matches("\\d+"), "eventId 应为雪花 ID");
        assertNotNull(event.snapshotRef());
        assertNotNull(event.occurredAt());
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(WorkingsetApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }
}
