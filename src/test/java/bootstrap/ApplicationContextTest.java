package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;

import alm.requirement.application.RequirementApplicationService;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.application.event.RequirementChanged;
import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/**
 * Spring Boot 上下文与 Spring Event 派发检查（S2 DoD：上下文可加载、事件经 Spring Event 发布）。
 */
class ApplicationContextTest {

    private static final Revision R3 = Revision.of(3);

    @Test
    void context_loads_without_web_and_exposes_composition_root_beans() {
        try (ConfigurableApplicationContext context = start()) {
            assertNotNull(context.getBean(WorkingSetApplicationService.class));
            assertNotNull(context.getBean(RequirementApplicationService.class));
            assertNotNull(context.getBean(IdentifierGenerator.class));
        }
    }

    @Test
    void requirement_changed_is_delivered_through_spring_event_synchronously() {
        try (ConfigurableApplicationContext context = start()) {
            List<RequirementChanged> received = new CopyOnWriteArrayList<>();
            context.addApplicationListener(
                    (ApplicationListener<PayloadApplicationEvent<RequirementChanged>>)
                            event -> received.add(event.getPayload()));
            RequirementApplicationService service = context.getBean(RequirementApplicationService.class);
            LocalId localId = LocalId.of("ctx-" + UUID.randomUUID());
            CommonId commonId = CommonId.of("ctx-c-" + UUID.randomUUID());
            TrackerId trackerId = TrackerId.of("ctx-t-" + UUID.randomUUID());

            service.create(localId, commonId, trackerId, "A", "source description", R3);
            service.changeTitle(localId, "B", LockVersion.of(0));

            assertEquals(1, received.size(), "Spring Event 应同步派发一条来源事件");
            RequirementChanged event = received.get(0);
            assertEquals(localId, event.requirementId());
            assertEquals(localId, event.sourceLocalId());
            assertEquals(R3, event.revision());
            assertTrue(String.valueOf(event.eventId()).matches("\\d+"), "eventId 应为雪花 ID");
            assertNotNull(event.snapshotRef());
            assertNotNull(event.occurredAt());
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(WorkingsetApplication.class)
                .web(WebApplicationType.NONE)
                .run();
    }
}
