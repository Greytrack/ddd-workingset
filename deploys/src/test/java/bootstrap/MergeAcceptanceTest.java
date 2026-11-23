package bootstrap;

import com.airedgesoft.alm.requirement.application.eventhandler.RequirementMergeApprovedListener;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryApprovedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.util.AopTestUtils;



import com.airedgesoft.alm.requirement.adapters.RequirementView;
import com.airedgesoft.alm.requirement.application.command.RequirementCommandService;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.infrastructure.repositories.JdbcRequirementRepository;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.adapters.MergeController.ComparisonPage;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService;
import com.airedgesoft.sharedkernel.workingset.application.command.MergeExecutionService.EntryRequest;
import com.airedgesoft.sharedkernel.workingset.application.query.MergeCheckpoint;
import com.airedgesoft.sharedkernel.workingset.application.query.MergeCheckpointQuery;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryResolvedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.ChangeKind;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Difference;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.Presence;
import com.airedgesoft.sharedkernel.workingset.domain.merge.Comparison.TrackerPair;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeDecision;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeDecision.FieldAction;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.ApplyResult;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.Outcome;
import com.airedgesoft.sharedkernel.workingset.domain.merge.MergeRound.State;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MergeAcceptanceTest {
    private final String suffix = UUID.randomUUID().toString();
    private final String child = id("child");
    private final String space = id("space");
    private final String classification = "14010101";
    private final String rootTracker = id("root");
    private final String sharedClassification = "14010102";
    private final String anchor = id("anchor");
    private final List<String> tokens = new ArrayList<>();
    private final List<String> rounds = new ArrayList<>();
    private final List<String> worksets = new ArrayList<>();
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private LightTrackerItemRepository items;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MergeCheckpointQuery checkpoints;
    @Autowired
    private IncompleteEventPublications publications;
    @Autowired
    private MergeExecutionService mergeExecution;
    @Autowired
    private RequirementCommandService requirements;
    @Autowired
    private JdbcRequirementRepository repository;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private RequirementMergeApprovedListener approvedListener;
    private String childTracker;
    private String childAnchor;

    private static Map<String, Object> existence(Difference diff, String action) {
        return Map.of("evidence", diff,
                "decision", Map.of("existence", action));
    }

    private static Map<String, Object> field(Difference diff, String action, boolean mark) {
        Map<String, String> fields = diff.fields().stream().collect(
                Collectors.toMap(Function.identity(), key -> action));
        return Map.of("evidence", diff,
                "decision", Map.of("fields", fields, "markAsMerged", mark));
    }

    private static Difference diff(ComparisonPage page, String family) {
        return page.differences().stream().filter(item -> family.equals(Long.toString(item.family()))).findFirst().orElseThrow();
    }

    private static String ws(String id) {
        return "/api/worksets/" + id;
    }

    private static String roundPath(String id) {
        return "/api/merge-rounds/" + id;
    }

    private static void assertStatus(HttpStatus status, ResponseEntity<?> response) {
        assertEquals(status, response.getStatusCode(), () -> String.valueOf(response.getBody()));
    }

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        FixtureIds.insertClassification(jdbc, classification, "UNSHARED");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, rootTracker, space, classification);
        createChild("1", child);
        mountSpaceWithLocalFolder("1");
        mountSpaceWithLocalFolder(child);
        String source = seed(rootTracker, anchor, "same");
        MergeAcceptanceTest.Derived derived = derive(child, rootTracker, source, anchor);
        childTracker = derived.trackerId();
        childAnchor = Long.toString(derived.item().localId());
    }

    @AfterEach
    void cleanup() {
        // Also remove rows created by tests that never produce a tracked MergeRound
        // (for example the direct checkpoint-insert test).
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE completion_date IS NULL
                  AND (event_type LIKE '%MergeEntryApprovedEvent'
                       OR event_type LIKE '%MergeEntryResolvedEvent')
                """, Integer.class) == 0);
        jdbc.update("DELETE FROM air_ws_merge_rounds WHERE request_id LIKE ?", "%" + suffix + "%");
        for (String round : rounds) {
            jdbc.update("DELETE FROM air_ws_items_merge_index WHERE batch_id = ?", round);
            jdbc.update("DELETE FROM air_ws_merge_rounds WHERE id = ?", round);
        }
        List<String> itemIds = jdbc.queryForList("""
                SELECT instance_id FROM air_inst_requirement_rev WHERE tracker IN
                    (SELECT tracker_id FROM trackers WHERE product_space_id = ?)
                UNION SELECT local_id FROM air_ws_items_index WHERE branch_id IN
                    (SELECT tracker_id FROM trackers WHERE product_space_id = ?)
                """, String.class, space, space);
        for (String local : itemIds) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + local + "%");
            jdbc.update("DELETE FROM cow_failures WHERE source_local_id = ?", local);

            jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", local);
            jdbc.update("DELETE FROM air_inst_requirement_rev WHERE instance_id = ?", local);
        }
        for (String workset : worksets.reversed()) {
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM air_ws_creations WHERE new_working_set_id = ?", workset);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", workset);
        }
        jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM air_inst_requirement_rev WHERE tracker IN (?, ?)", rootTracker, childTracker);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void explicitlyCreatesDeletesAndUpdatesInEitherDirection(boolean towardsChild) {
        String source = towardsChild ? "1" : child;
        String target = towardsChild ? child : "1";
        String sourceTracker = towardsChild ? rootTracker : childTracker;
        String targetTracker = towardsChild ? childTracker : rootTracker;
        String add = id("add");
        String remove = id("remove");
        String edit = id("edit");
        seed(sourceTracker, add, "new");
        String removedId = seed(targetTracker, remove, "to delete");
        seed(sourceTracker, edit, "source");
        String editedId = seed(targetTracker, edit, "target");

        ComparisonPage compared = compare(source, target);
        assertEquals(Presence.ABSENT, diff(compared, add).target().presence());
        assertEquals(Presence.ABSENT, diff(compared, remove).source().presence());
        assertEquals(ChangeKind.ADDED, diff(compared, add).kind());
        assertEquals(ChangeKind.DELETED, diff(compared, remove).kind());
        assertEquals(ChangeKind.MODIFIED, diff(compared, edit).kind());
        assertEquals(Long.valueOf(source), diff(compared, add).trackers().sourceWorkingSetId());
        assertEquals(Long.valueOf(target), diff(compared, add).trackers().targetWorkingSetId());
        assertTrue(diff(compared, add).fields().isEmpty());
        assertTrue(diff(compared, remove).fields().isEmpty());
        assertTrue(diff(compared, edit).fields().contains("title"));
        assertFalse(compared.differences().stream().anyMatch(item -> anchor.equals(Long.toString(item.family()))));
        String round = submit(target, compared, existence(diff(compared, add), "ACCEPT"),
                existence(diff(compared, remove), "ACCEPT"), field(diff(compared, edit), "ACCEPT", false));
        MergeRound.ApplyResult result = awaitResult(round);
        assertEquals(State.Succeeded, result.state());
        assertEquals(2, result.summary().applied());
        assertEquals(1, result.summary().confirmed());
        assertEquals("source", requirement(editedId).title());
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + removedId, Map.class));
        assertTrue(jdbc.queryForObject("SELECT delete_flag = 1 FROM air_inst_requirement_rev WHERE instance_id = ? ORDER BY revision DESC LIMIT 1", Boolean.class, removedId));
        assertEquals(1, confirmations(round, add));
        assertEquals(0, confirmations(round, remove));
        assertEquals("new", jdbc.queryForObject(
                "SELECT name FROM air_inst_requirement_rev WHERE tracker = ? AND common_id = ? ORDER BY revision DESC LIMIT 1", String.class, targetTracker, add));
        assertEquals(result, apply(round));

        // Soft deletion is shown as an addition but preserves the original target identity.
        seed(sourceTracker, remove, "present again");
        ComparisonPage afterDelete = compare(source, target);
        Difference recoverable = diff(afterDelete, remove);
        assertEquals(ChangeKind.ADDED, recoverable.kind());
        assertEquals(Presence.DELETED, recoverable.target().presence());
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + removedId, Map.class));
        String restoreRound = submit(target, afterDelete, existence(recoverable, "ACCEPT"));
        MergeRound.ItemResult restored = awaitResult(restoreRound).itemResults().getFirst();
        assertEquals(Outcome.Confirmed, restored.outcome());
        assertEquals(removedId, restored.businessRef());
        assertEquals("present again", requirement(removedId).title());
        assertEquals(1, confirmations(restoreRound, remove));
    }

    @Test
    void ignoredPresenceChangesHaveNoCheckpointAndUpdatesAcceptOnlySelectedFields() {
        String added = id("ignored-add");
        seed(rootTracker, added, "source addition");
        String deleted = id("ignored-delete");
        String kept = seed(childTracker, deleted, "target retained");
        String edited = id("mixed-field-update");
        String source = seed(rootTracker, edited, "source title");
        jdbc.update("UPDATE air_inst_requirement_rev SET description = 'source description' WHERE id = (SELECT id FROM air_inst_requirement_rev WHERE instance_id = ? ORDER BY revision DESC LIMIT 1)", source);
        String target = seed(childTracker, edited, "target title");
        ComparisonPage first = compare("1", child);
        Difference update = diff(first, edited);
        String round = submit(child, first, existence(diff(first, added), "IGNORED"),
                existence(diff(first, deleted), "IGNORED"), Map.of("evidence", update,
                        "decision", Map.of("fields", Map.of("title", "ACCEPT", "description", "IGNORED"),
                                "markAsMerged", false)));
        assertEquals(State.Succeeded, awaitResult(round).state());
        assertEquals("source title", requirement(target).title());
        assertEquals("description", requirement(target).description());
        assertEquals("target retained", requirement(kept).title());
        assertEquals(0, confirmations(round, added));
        assertEquals(0, confirmations(round, deleted));
        assertEquals(0, confirmations(round, edited));
        ComparisonPage next = compare("1", child);
        assertNotNull(diff(next, added));
        assertNotNull(diff(next, deleted));
        assertEquals(List.of("description"), diff(next, edited).fields());
        String confirmed = submit(child, next, field(diff(next, edited), "IGNORED", true));
        assertEquals(Outcome.Confirmed, awaitResult(confirmed).itemResults().getFirst().outcome());
        assertEquals(1, confirmations(confirmed, edited));
        assertFalse(compare("1", child).differences().stream().anyMatch(item -> edited.equals(Long.toString(item.family()))));
    }

    @Test
    void submittedDecisionsRunAsAsyncRoundAndPollToTerminal() {
        String family = id("async");
        seed(rootTracker, family, "source");
        String target = seed(childTracker, family, "target");
        ComparisonPage compared = compare("1", child);
        Difference difference = diff(compared, family);
        ResponseEntity<MergeRound> response = rest.postForEntity(ws(child) + "/merge-rounds",
                Map.of("sourceWorkingSetId", "1", "requestId", "async-" + family,
                        "decisions", List.of(field(difference, "ACCEPT", false))), MergeRound.class);
        assertStatus(HttpStatus.CREATED, response);
        String round = response.getBody().id();
        rounds.add(round);
        tokens.add(response.getBody().token());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            MergeRound current = rest.getForObject(roundPath(round), MergeRound.class);
            assertNotNull(current.result());
            assertEquals(State.Succeeded, current.result().state());
        });
        assertEquals("source", requirement(target).title());
    }

    @Test
    void duplicateSubmittedDecisionsReturnTheSameRound() {
        String family = id("idem");
        seed(rootTracker, family, "source");
        String target = seed(childTracker, family, "target");
        Difference difference = diff(compare("1", child), family);
        String requestId = id("request");
        Map<String, Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
                "decisions", List.of(field(difference, "ACCEPT", false)));
        ResponseEntity<MergeRound> first = rest.postForEntity(ws(child) + "/merge-rounds", body, MergeRound.class);
        assertStatus(HttpStatus.CREATED, first);
        ResponseEntity<MergeRound> second = rest.postForEntity(ws(child) + "/merge-rounds", body, MergeRound.class);
        assertStatus(HttpStatus.CREATED, second);
        assertEquals(first.getBody().id(), second.getBody().id());
        String round = first.getBody().id();
        rounds.add(round);
        tokens.add(first.getBody().token());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            MergeRound current = rest.getForObject(roundPath(round), MergeRound.class);
            assertNotNull(current.result());
            assertEquals(State.Succeeded, current.result().state());
        });
        assertEquals("source", requirement(target).title());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM air_ws_merge_rounds WHERE request_id = ?", Integer.class, requestId));
    }

    @Test
    void rejectsMissingDecisionsMissingRequestIdAndOversizedPage() {
        String family = id("limits");
        seed(rootTracker, family, "limit probe");
        Difference difference = diff(compare("1", child), family);
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds",
                Map.of("sourceWorkingSetId", "1", "requestId", id("empty")), Map.class));
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds",
                Map.of("sourceWorkingSetId", "1", "decisions", List.of(field(difference, "ACCEPT", false))), Map.class));
        List<Map<String, Object>> oversized = IntStream.range(0, 51)
                .mapToObj(index -> field(difference, "ACCEPT", false)).toList();
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds",
                Map.of("sourceWorkingSetId", "1", "requestId", id("oversized"), "decisions", oversized), Map.class));
    }

    @Test
    void concurrentSubmissionsWithSameRequestIdReturnOneRound() throws Exception {
        String family = id("concurrent-submit");
        seed(rootTracker, family, "concurrent submit probe");
        Difference difference = diff(compare("1", child), family);
        String requestId = id("concurrent-request");
        Map<String, Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
                "decisions", List.of(existence(difference, "ACCEPT")));
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ResponseEntity<Map>> one = executor.submit(() -> {
                start.await();
                return rest.postForEntity(ws(child) + "/merge-rounds", body, Map.class);
            });
            Future<ResponseEntity<Map>> two = executor.submit(() -> {
                start.await();
                return rest.postForEntity(ws(child) + "/merge-rounds", body, Map.class);
            });
            start.countDown();
            ResponseEntity<Map> first = one.get(20, TimeUnit.SECONDS);
            ResponseEntity<Map> second = two.get(20, TimeUnit.SECONDS);
            assertStatus(HttpStatus.CREATED, first);
            assertStatus(HttpStatus.CREATED, second);
            assertEquals(first.getBody().get("id"), second.getBody().get("id"));
            rounds.add((String) first.getBody().get("id"));
            tokens.add((String) first.getBody().get("token"));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM air_ws_merge_rounds WHERE request_id = ?",
                Integer.class, requestId));
    }

    @Test
    void failedSubmissionLeavesNoRoundBehind() {
        String family = id("failed-submit");
        String source = seed(rootTracker, family, "failed submit probe");
        ComparisonPage frozen = compare("1", child);
        change(source, "changed before submit", 0);
        String requestId = id("failed-submit-request");
        Map<String, Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
                "decisions", List.of(field(diff(frozen, family), "ACCEPT", false)));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(child) + "/merge-rounds", body, Map.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_ws_merge_rounds WHERE request_id = ?",
                Integer.class, requestId));
    }

    @Test
    void rejectsRequestIdLongerThanStorageColumn() {
        String family = id("long-request");
        seed(rootTracker, family, "long request id probe");
        Difference difference = diff(compare("1", child), family);
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds",
                Map.of("sourceWorkingSetId", "1", "requestId", "x".repeat(129),
                        "decisions", List.of(field(difference, "ACCEPT", false))), Map.class));
    }

    @Test
    void requestIdCannotBeReusedForAnotherDirection() {
        String family = id("conflict");
        seed(rootTracker, family, "conflict probe");
        Difference difference = diff(compare("1", child), family);
        String requestId = id("same-request");
        ResponseEntity<MergeRound> accepted = rest.postForEntity(ws(child) + "/merge-rounds", Map.of("sourceWorkingSetId", "1",
                "requestId", requestId, "decisions", List.of(existence(difference, "ACCEPT"))), MergeRound.class);
        assertStatus(HttpStatus.CREATED, accepted);
        rounds.add(accepted.getBody().id());
        tokens.add(accepted.getBody().token());
        ResponseEntity<Map> conflict = rest.postForEntity(ws("1") + "/merge-rounds", Map.of("sourceWorkingSetId", child,
                "requestId", requestId, "decisions", List.of(existence(difference, "ACCEPT"))), Map.class);
        assertStatus(HttpStatus.CONFLICT, conflict);
        assertEquals("Conflict", conflict.getBody().get("reason"));
    }

    @Test
    void mergesNativeChildItemBackToDefaultParentWithFamilyIdentity() {
        assertNativeMergeBack("1", child, rootTracker, childTracker);
    }

    @Test
    void mergesNativeChildItemBackToNonDefaultParentAndRestoresItsOriginalVariant() {
        String grandchild = id("grandchild");
        createChild(child, grandchild);
        mountSpaceWithLocalFolder(grandchild);
        String grandTracker = derive(grandchild, childTracker, childAnchor, anchor).trackerId();
        assertNativeMergeBack(child, grandchild, childTracker, grandTracker);
    }

    private void assertNativeMergeBack(String parent, String sourceWs, String parentTracker, String sourceTracker) {
        RequirementView source = createRequirement(sourceWs, sourceTracker, "native child item");
        ComparisonPage comparison = compare(sourceWs, parent, sourceTracker, parentTracker);
        Difference difference = diff(comparison, source.commonId());
        assertEquals(Presence.ABSENT, difference.target().presence());
        String round = submit(parent, comparison, existence(difference, "ACCEPT"));
        MergeRound.ApplyResult created = awaitResult(round);
        assertEquals(State.Succeeded, created.state());
        String parentLocal = created.itemResults().getFirst().businessRef();
        RequirementView target = requirement(parentLocal);
        assertEquals(parentTracker, target.trackerId());
        assertEquals(source.commonId(), target.commonId());
        assertEquals(source.number(), target.number());
        assertFalse(source.localId().equals(target.localId()));
        assertEquals("1".equals(parent), target.localId().equals(target.commonId()));
        assertEquals(source.title(), target.title());
        assertEquals(created, apply(round));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertTrue(items.findById(Long.valueOf(parentLocal)).isPresent()));

        change(source.localId(), "updated child", 0);
        assertTrue(compare(sourceWs, parent, sourceTracker, parentTracker).differences().stream()
                .noneMatch(item -> source.commonId().equals(Long.toString(item.family()))));

        assertStatus(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + parentLocal + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + parentLocal, Map.class));
        ComparisonPage afterDelete = compare(sourceWs, parent, sourceTracker, parentTracker);
        Difference absent = diff(afterDelete, source.commonId());
        assertEquals(Presence.DELETED, absent.target().presence());
        assertTrue(submitFails(parent, afterDelete, existence(absent, "RESTORE")));
        ComparisonPage fresh = compare(sourceWs, parent, sourceTracker, parentTracker);
        String restoreRound = submit(parent, fresh, existence(diff(fresh, source.commonId()), "ACCEPT"));
        MergeRound.ApplyResult restored = awaitResult(restoreRound);
        assertEquals(State.Succeeded, restored.state(), restored::toString);
        assertEquals(parentLocal, restored.itemResults().getFirst().businessRef());
        assertEquals(Outcome.Confirmed, restored.itemResults().getFirst().outcome());
        assertEquals("RESTORE", restored.itemResults().getFirst().operation());
        assertEquals(1, confirmations(restoreRound, source.commonId()));
        assertEquals("updated child", requirement(parentLocal).title());
        assertEquals(target.revision(), requirement(parentLocal).revision());
        assertEquals(source.commonId(), requirement(source.localId()).commonId());
        assertEquals(sourceWs, rest.getForObject(ws(sourceWs), WorkingSetController.WorkingSetView.class).workingSetId());
        assertEquals(parent, rest.getForObject(ws(sourceWs), WorkingSetController.WorkingSetView.class).parentWorkingSetId());
        assertEquals(restored, apply(restoreRound));
    }

    @Test
    void secondRoundWithRacedEvidenceCannotCommitTheSameFamilyTwice() {
        RequirementView source = createRequirement(child, childTracker, "raced child item");
        ComparisonPage first = compare(child, "1");
        ComparisonPage stale = compare(child, "1");
        String firstRound = submit("1", first, existence(diff(first, source.commonId()), "ACCEPT"));
        assertEquals(Outcome.Confirmed, awaitResult(firstRound).itemResults().getFirst().outcome());
        assertEquals("StaleEvidence", submitFailure("1", stale, existence(diff(stale, source.commonId()), "ACCEPT")));
        assertEquals(source.title(), requirement(source.commonId()).title());
    }

    private RequirementView createRequirement(String workset, String tracker, String title) {
        ResponseEntity<RequirementView> response = rest.postForEntity("/api/requirements", Map.of("workingSetId", workset,
                        "trackerId", tracker, "title", title, "description", "description", "revision", 3),
                RequirementView.class);
        assertStatus(HttpStatus.CREATED, response);
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE local_id = ?)",
                Boolean.class, response.getBody().localId())));
        return response.getBody();
    }

    @Test
    void registrationRejectsChangedEvidenceAndRevision() {
        String edit = id("edit");
        String source = seed(rootTracker, edit, "source");
        String target = seed(childTracker, edit, "target");
        ComparisonPage frozen = compare("1", child);
        change(source, "changed before submit", 0);
        assertEquals("StaleEvidence",
                submitFailure(child, frozen, field(diff(frozen, edit), "ACCEPT", false)));

        change(source, "latest source", 1);
        ComparisonPage fresh = compare("1", child);
        String round = submit(child, fresh, field(diff(fresh, edit), "ACCEPT", false));
        assertEquals(State.Succeeded, awaitResult(round).state());
        assertEquals("latest source", requirement(target).title());

        change(source, "different", 2);
        ComparisonPage beforeRevision = compare("1", child);
        requirements.upgradeRevision(Long.valueOf(target), Long.valueOf(1));
        assertEquals("StaleEvidence",
                submitFailure(child, beforeRevision, field(diff(beforeRevision, edit), "ACCEPT", false)));
        assertEquals("latest source", requirement(target).title());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void executionUsesApprovedValuesAndTheBusinessListenerChecksTargetConcurrency(boolean editTarget) {
        String family = id("approved");
        String source = seed(rootTracker, family, "approved source");
        String target = seed(childTracker, family, "original target");
        Difference selected = diff(compare("1", child), family);
        EntryRequest request = new EntryRequest(selected, new MergeDecision(Map.of("title", FieldAction.ACCEPT), null, true));

        // Publish the round submission only after the concurrent edit has committed.
        String round = transactions.execute(tx -> {
            MergeRound submitted = mergeExecution.submit(Long.valueOf(child), Long.valueOf("1"),
                    UUID.randomUUID().toString(), List.of(request), "1");
            requirements.changeTitle(Long.valueOf(editTarget ? target : source), "edited after approval", Long.valueOf(0));
            return submitted.id();
        });
        rounds.add(round);

        ApplyResult result = awaitResult(round);
        assertEquals(editTarget ? Outcome.Conflict : Outcome.Confirmed, result.itemResults().getFirst().outcome());
        assertEquals(editTarget ? "edited after approval" : "approved source", requirement(target).title());
    }

    @Test
    void partialRoundRetainsKeepTargetCheckpointAndNewRoundOnlyProcessesSelectedItems() {
        String keep = id("keep");
        String rejected = id("rejected");
        String deferred = id("deferred");
        seed(rootTracker, keep, "source");
        String kept = seed(childTracker, keep, "target");
        String rejectedSource = seed(rootTracker, rejected, "reviewed");
        String rejectedTarget = seed(childTracker, rejected, "target");
        seed(rootTracker, deferred, "source");
        seed(childTracker, deferred, "target");
        ComparisonPage comparison = compare("1", child);
        assertTrue(submitFails(child, comparison, field(diff(comparison, deferred), "DEFER", true)));
        jdbc.update("UPDATE air_inst_requirement_rev SET name = '', lock_version = 1 WHERE id = (SELECT id FROM air_inst_requirement_rev WHERE instance_id = ? ORDER BY revision DESC LIMIT 1)", rejectedSource);
        ComparisonPage withInvalidSource = compare("1", child);
        String round = submit(child, withInvalidSource, field(diff(withInvalidSource, keep), "IGNORED", true),
                field(diff(withInvalidSource, rejected), "ACCEPT", false),
                field(diff(withInvalidSource, deferred), "IGNORED", false));
        MergeRound.ApplyResult partial = awaitResult(round);
        assertEquals(State.PartiallySucceeded, partial.state());
        assertEquals(List.of(Outcome.Confirmed, Outcome.Rejected, Outcome.Applied),
                partial.itemResults().stream().map(MergeRound.ItemResult::outcome).toList());
        assertEquals(0, requirement(kept).lockVersion());
        assertEquals("target", requirement(rejectedTarget).title());

        MergeCheckpoint.Key key = new MergeCheckpoint.Key(Long.valueOf(rootTracker), Long.valueOf(childTracker),
                Long.valueOf(keep));
        MergeCheckpoint checkpoint = checkpoints.findLatest(key).orElseThrow();
        assertNotNull(checkpoint.localIdA());
        assertNotNull(checkpoint.localIdB());
        assertTrue(kept.equals(Long.toString(checkpoint.localIdA())) || kept.equals(Long.toString(checkpoint.localIdB())));
        change(kept, "changed after checkpoint", 0);
        ComparisonPage afterCheckpointEdit = compare("1", child);
        assertFalse(afterCheckpointEdit.differences().stream().anyMatch(item -> keep.equals(Long.toString(item.family()))));
        change(rejectedSource, "valid on retry", 1);
        ComparisonPage retryComparison = compare("1", child);
        assertFalse(retryComparison.differences().stream().anyMatch(item -> keep.equals(Long.toString(item.family()))));
        String retryRound = submit(child, retryComparison, field(diff(retryComparison, rejected), "ACCEPT", false));
        assertEquals(1, awaitResult(retryRound).summary().applied());
        assertEquals("valid on retry", requirement(rejectedTarget).title());
        assertEquals(partial, rest.getForObject(roundPath(round), MergeRound.class).result());

        assertEquals(MergeCheckpoint.Action.IGNORED, checkpoint.action());

    }

    @Test
    void repeatedApplyIsIdempotentAndRecordsOnlyTheCommittedWrite() throws Exception {
        String family = id("resume");
        String source = seed(rootTracker, family, "accepted");
        String target = seed(childTracker, family, "before");
        String laterFamily = id("resume-later");
        seed(rootTracker, laterFamily, "later accepted");
        String laterTarget = seed(childTracker, laterFamily, "later before");
        ComparisonPage allDifferences = compare("1", child);

        String round = submit(child, allDifferences, field(diff(allDifferences, family), "ACCEPT", true),
                field(diff(allDifferences, laterFamily), "ACCEPT", false));
        MergeRound.ApplyResult result = apply(round);

        assertEquals(State.Succeeded, result.state());
        assertEquals(Outcome.Confirmed, result.itemResults().getFirst().outcome());
        assertEquals(target, result.itemResults().getFirst().businessRef());
        assertEquals("accepted", requirement(target).title());
        assertEquals("later accepted", requirement(laterTarget).title());

        // Apply 只负责幂等调度；重复请求不会再次应用业务写入。
        assertEquals(result, apply(round));
        assertEquals("accepted", requirement(target).title());
        assertEquals("later accepted", requirement(laterTarget).title());

        String approved = jdbc.queryForObject("""
                SELECT serialized_event FROM event_publication
                WHERE event_type LIKE '%.MergeEntryApprovedEvent'
                  AND serialized_event::jsonb ->> 'roundId' = ?
                  AND serialized_event::jsonb ->> 'localId' = ?
                """, String.class, round, target);
        MergeEntryApprovedEvent replay = new ObjectMapper().readValue(approved, MergeEntryApprovedEvent.class);
        long lockVersion = requirement(target).lockVersion();
        RequirementMergeApprovedListener listener = AopTestUtils.getTargetObject(approvedListener);
        transactions.executeWithoutResult(tx -> listener.on(replay));
        assertEquals(lockVersion, requirement(target).lockVersion());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%.MergeEntryResolvedEvent'
                  AND serialized_event::jsonb ->> 'roundId' = ? AND serialized_event::jsonb ->> 'entryId' = ?
                """, Integer.class, replay.roundId(), replay.entryId()));

        MergeCheckpoint checkpoint = checkpoints.findLatest(new MergeCheckpoint.Key(
                Long.valueOf(rootTracker), Long.valueOf(childTracker), Long.valueOf(family))).orElseThrow();
        assertTrue(checkpoint.revisionA() == 3 || checkpoint.revisionB() == 3);
    }

    private int pendingMergePublications(String localId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE '%RequirementMergeAppliedEvent' AND serialized_event LIKE ?
                AND completion_date IS NULL
                """, Integer.class, "%" + localId + "%");
    }

    @Test
    void checkpointUsesTargetOwnRevisionWhileUnmaterializedContentReadsSourceRevision() {
        String grandchild = id("grandchild");
        createChild(child, grandchild);
        mountSpaceWithLocalFolder(grandchild);
        String parentItem = childTracker;
        String parentLocal = jdbc.queryForObject("SELECT local_id FROM air_ws_items_index WHERE branch_id = ?",
                String.class, parentItem);
        MergeAcceptanceTest.Derived grand = derive(grandchild, childTracker, parentLocal, anchor);
        ItemIndexFixture.updateRevision(jdbc, Long.toString(grand.item().localId()), 1);

        jdbc.update("""
                INSERT INTO air_inst_requirement_rev (instance_id, common_id, tracker, name, description, revision, lock_version,
                    workingset_id, created_by, created_time)
                VALUES (?, ?, ?, 'parent local', 'description', 3, 0,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, parentLocal, anchor, childTracker, childTracker, System.currentTimeMillis());
        // The grandchild already points to the original source when its parent materializes.
        jdbc.update("""
                UPDATE air_ws_items_index
                SET has_copied = 1, source_local_id = local_id, source_revision = revision
                WHERE local_id = ?
                """, parentLocal);

        ComparisonPage compared = compare(child, grandchild, childTracker, grand.trackerId());
        Difference item = diff(compared, anchor);
        assertEquals(Long.toString(grand.item().localId()), Long.toString(item.target().localId()));
        assertFalse(item.target().localId().equals(item.target().contentId()));
        assertEquals(1, item.target().revision());
        assertEquals(3, item.target().contentRevision());
        assertEquals("same", item.target().fields().get("title"));
        String round = submit(grandchild, compared, field(item, "ACCEPT", true));
        assertEquals(Outcome.Confirmed, awaitResult(round).itemResults().getFirst().outcome());
        assertEquals("parent local", requirement(Long.toString(grand.item().localId())).title());
        MergeCheckpoint.Key key = new MergeCheckpoint.Key(Long.valueOf(childTracker), Long.valueOf(grand.trackerId()), Long.valueOf(anchor));
        MergeCheckpoint checkpoint = checkpoints.findLatest(key).orElseThrow();
        boolean sourceIsA = key.a().equals(Long.valueOf(childTracker));
        assertEquals(3, sourceIsA ? checkpoint.revisionA() : checkpoint.revisionB());
        assertEquals(1, sourceIsA ? checkpoint.revisionB() : checkpoint.revisionA());
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_copied = 1 FROM air_ws_items_index WHERE local_id = ?",
                Boolean.class, Long.toString(grand.item().localId()))));
        change(Long.toString(grand.item().localId()), "ordinary target edit", 0);
        assertTrue(compare(child, grandchild, childTracker, grand.trackerId()).differences().stream()
                .noneMatch(candidate -> anchor.equals(Long.toString(candidate.family()))));
        requirements.upgradeRevision(Long.valueOf(parentLocal), Long.valueOf(0));
        assertNotNull(diff(compare(child, grandchild, childTracker, grand.trackerId()), anchor));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity("/api/requirements/" + Long.toString(grand.item().localId())
                + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + Long.toString(grand.item().localId()), Map.class));
    }

    @Test
    void rejectsNonAdjacentWorkingSetsAndTrackersOutsideAValidPair() {
        String sibling = id("sibling");
        String grandchild = id("grandchild");
        createChild("1", sibling);
        createChild(child, grandchild);
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(comparePath(sibling, child, rootTracker, childTracker), null, Map.class));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(comparePath("1", grandchild, rootTracker, childTracker), null, Map.class));
        String shared = id("shared");
        FixtureIds.insertClassification(jdbc, sharedClassification, "SHARED");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'SHARED')
                """, shared, space, sharedClassification);
        FixtureIds.extendScope(rest, child, WorkingSetCreation.Status.Succeeded, shared);
        RequirementView sharedItem = createRequirement(child, shared, "shared child entry");
        assertEquals(sharedItem.localId(), sharedItem.commonId());
        assertTrue(compare(child, "1").differences().stream()
                .noneMatch(item -> sharedItem.commonId().equals(Long.toString(item.family()))));
        assertStatus(HttpStatus.CONFLICT,
                rest.postForEntity(comparePath("1", child, shared, childTracker), null, Map.class));
        assertStatus(HttpStatus.CONFLICT,
                rest.postForEntity(comparePath(child, grandchild, childTracker, id("missing")), null, Map.class));
    }

    @Test
    void compareRequiresBothTrackerIds() {
        String path = ws(child) + "/compare/1";
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(path + "?sourceTrackerId=" + rootTracker, null, Map.class));
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(path + "?targetTrackerId=" + childTracker, null, Map.class));
    }

    @Test
    void checkpointHitIsDirectionAgnostic() {
        String family = id("direction-agnostic");
        seed(rootTracker, family, "source");
        seed(childTracker, family, "target");
        ComparisonPage forward = compare("1", child);
        assertEquals(State.Succeeded, awaitResult(submit(child, forward,
                field(diff(forward, family), "ACCEPT", false))).state());
        ComparisonPage backward = compare(child, "1");
        assertFalse(backward.differences().stream().anyMatch(item -> family.equals(Long.toString(item.family()))));
    }

    @Test
    void concurrentPairSubmissionIsRejected() {
        String family = id("pair-busy");
        seed(rootTracker, family, "source");
        Difference difference = diff(compare("1", child), family);
        String low = rootTracker.compareTo(childTracker) < 0 ? rootTracker : childTracker;
        String high = low.equals(rootTracker) ? childTracker : rootTracker;
        jdbc.update("""
                INSERT INTO air_ws_merge_rounds (id, token, request_id, state, tracker_a_id, tracker_b_id)
                VALUES (?, ?, ?, CAST('{"state":"Running"}' AS jsonb), ?, ?)
                """, id("busy-round"), id("busy-token"), id("busy-request"), low, high);
        rounds.add(id("busy-round"));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("pair-request"),
                "decisions", List.of(existence(difference, "ACCEPT"))), Map.class));
    }

    @Test
    void pairCanMergeAgainAfterTerminal() {
        String family = id("pair-again");
        seed(rootTracker, family, "source");
        Difference first = diff(compare("1", child), family);
        assertEquals(State.Succeeded, awaitResult(submit(child, compare("1", child),
                existence(first, "ACCEPT"))).state());
        String sourceLocal = jdbc.queryForObject(
                "SELECT instance_id FROM air_inst_requirement_rev WHERE tracker = ? AND common_id = ? ORDER BY revision DESC LIMIT 1",
                String.class, rootTracker, family);
        jdbc.update("UPDATE air_inst_requirement_rev SET revision = revision + 1, name = ? WHERE id = (SELECT id FROM air_inst_requirement_rev WHERE instance_id = ? ORDER BY revision DESC LIMIT 1)",
                "changed after first round", sourceLocal);
        Difference second = diff(compare("1", child), family);
        ResponseEntity<MergeRound> secondRound = rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("pair-again-request"),
                "decisions", List.of(field(second, "ACCEPT", false))), MergeRound.class);
        assertStatus(HttpStatus.CREATED, secondRound);
        rounds.add(secondRound.getBody().id());
    }

    @Test
    void mixedTrackerPairsAreRejected() {
        String family = id("mixed");
        seed(rootTracker, family, "source");
        Difference difference = diff(compare("1", child), family);
        Difference other = new Difference(difference.kind(), difference.family(),
                new Comparison.TrackerPair(Long.valueOf("1"),
                        Long.valueOf("265564874633152"), Long.valueOf(child), Long.valueOf("264912685293504")),
                difference.source(), difference.target(), difference.fields());
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("mixed-request"),
                "decisions", List.of(existence(difference, "ACCEPT"), existence(other, "ACCEPT"))), Map.class));
    }

    @Test
    void candidatePairsFollowDirectSourceIdOnly() {
        String secondRoot = id("root2");
        String secondChild = id("child2");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, secondRoot, space, classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id,
                    sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, secondChild, secondRoot, child, space, classification);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id,
                    sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                """, child, secondChild, space, classification);

        String firstFamily = id("first-family");
        String secondFamily = id("second-family");
        seed(rootTracker, firstFamily, "first");
        seed(secondRoot, secondFamily, "second");
        TrackerPair first = new TrackerPair(Long.valueOf("1"), Long.valueOf(rootTracker),
                Long.valueOf(child), Long.valueOf(childTracker));
        TrackerPair second = new TrackerPair(Long.valueOf("1"), Long.valueOf(secondRoot),
                Long.valueOf(child), Long.valueOf(secondChild));

        ComparisonPage page = compare("1", child);
        assertEquals(List.of(first), page.candidates());
        assertEquals(List.of(firstFamily), page.differences().stream().map(d -> Long.toString(d.family())).toList());
        assertEquals(List.of(second), compare("1", child, secondRoot, secondChild).candidates());
        assertStatus(HttpStatus.CONFLICT,
                rest.postForEntity(comparePath("1", child, secondRoot, childTracker), null, Map.class));
        assertStatus(HttpStatus.CONFLICT,
                rest.postForEntity(comparePath(child, "1", rootTracker, childTracker), null, Map.class));
    }

    @Test
    void mixedWorkingSetContextsAreRejectedEvenWithTheSameTrackerPair() {
        String family = id("mixed-ws");
        seed(rootTracker, family, "source");
        Difference difference = diff(compare("1", child), family);
        Difference other = new Difference(difference.kind(), difference.family(),
                new Comparison.TrackerPair(Long.valueOf(child),
                        difference.trackers().source(), Long.valueOf("1"), difference.trackers().target()),
                difference.source(), difference.target(), difference.fields());
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("mixed-ws-request"),
                "decisions", List.of(existence(difference, "ACCEPT"), existence(other, "ACCEPT"))), Map.class));
    }

    @Test
    void confirmationFailureRollsBackRoundAndOldBusinessReplayCannotReplaceNewRound() {
        String family = id("checkpoint-replay");
        String source = seed(rootTracker, family, "source");
        String target = seed(childTracker, family, "target");
        String other = id("competing-family");
        seed(rootTracker, other, "other source");
        seed(childTracker, other, "other target");
        ComparisonPage first = compare("1", child);
        String constraint = "test_checkpoint_" + suffix.replace("-", "");
        jdbc.execute("ALTER TABLE air_ws_items_merge_index ADD CONSTRAINT " + constraint
                + " CHECK (common_id <> " + Long.parseLong(family) + ") NOT VALID");
        String firstRound;
        MergeEntryResolvedEvent replay;
        MergeCheckpoint.Key key = new MergeCheckpoint.Key(Long.valueOf(rootTracker), Long.valueOf(childTracker),
                Long.valueOf(family));
        try {
            firstRound = submit(child, first, field(diff(first, family), "ACCEPT", true));
            await().atMost(Duration.ofSeconds(8)).until(() -> "source".equals(requirement(target).title()));
            MergeRound snapshot = roundState(firstRound);
            replay = new MergeEntryResolvedEvent(firstRound,
                    snapshot.entries().getFirst().id(), MergeEntryResolvedEvent.Outcome.APPLIED,
                    Long.valueOf(target), 3, 3, false, false, null);
            assertThrows(DomainException.class, () -> mergeExecution.recordResult(replay));
            assertEquals(State.Running, roundState(firstRound).state());
            Assertions.assertNull(roundState(firstRound).entries().getFirst().result());
            assertTrue(checkpoints.findLatest(key).isEmpty());
            ComparisonPage competing = compare("1", child);
            assertEquals("PairMergeInProgress", submitFailure(child, competing,
                    field(diff(competing, other), "IGNORED", true)));
        } finally {
            jdbc.execute("ALTER TABLE air_ws_items_merge_index DROP CONSTRAINT " + constraint);
        }
        long appliedVersion = requirement(target).lockVersion();
        publications.resubmitIncompletePublications(publication -> replay.equals(publication.getEvent()));
        assertEquals(State.Succeeded, awaitResult(firstRound).state());
        assertEquals(appliedVersion, requirement(target).lockVersion());

        jdbc.update("UPDATE air_inst_requirement_rev SET revision = 4, name = 'source revised' WHERE id = (SELECT id FROM air_inst_requirement_rev WHERE instance_id = ? ORDER BY revision DESC LIMIT 1)", source);
        ComparisonPage next = compare("1", child);
        String secondRound = submit(child, next, field(diff(next, family), "IGNORED", true));
        assertEquals(State.Succeeded, awaitResult(secondRound).state());
        MergeCheckpoint latest = checkpoints.findLatest(key).orElseThrow();
        assertEquals(secondRound, latest.roundId());

        transactions.executeWithoutResult(tx -> events.publishEvent(replay));
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals(2, jdbc.queryForObject("""
                    SELECT count(*) FROM event_publication
                    WHERE event_type LIKE '%MergeEntryResolvedEvent' AND serialized_event LIKE ?
                      AND completion_date IS NOT NULL
                    """, Integer.class, "%" + firstRound + "%"));
        });
        assertEquals(latest, checkpoints.findLatest(key).orElseThrow());
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM air_ws_items_merge_index WHERE common_id = ?
                """, Integer.class, Long.valueOf(family)));
        // A V3 revocation row hides the current confirmation, rather than revealing the previous one.
        jdbc.update("""
                        INSERT INTO air_ws_items_merge_index (id, batch_id, branch_a_id, branch_b_id, common_id, merge_status)
                        VALUES (?, ?, ?, ?, ?, 'REVOKED')
                        """, Long.valueOf(id("legacy-revoked")), Long.valueOf(secondRound), key.a(),
                key.b(), key.family());
        assertTrue(checkpoints.findLatest(key).orElseThrow().revoked());
        assertNotNull(diff(compare("1", child), family));
    }

    @Test
    void publicDeleteUndeleteAndDelete2DistinguishMarkedAndPermanentlyRemovedItems() {
        String local = seed(rootTracker, id("life"), "lifecycle");
        String path = "/api/requirements/" + local;
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity(path, Map.class));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/undelete", null, Void.class));
        assertEquals("lifecycle", requirement(local).title());
        Requirement staleUpdate = repository.findById(Long.valueOf(local)).orElseThrow().changeTitle("stale", Long.valueOf(2));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(path + "/delete2", null, Map.class));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete2", null, Void.class));
        assertThrows(DomainException.class, () -> repository.save(staleUpdate));
        assertStatus(HttpStatus.NOT_FOUND, rest.postForEntity(path + "/undelete", null, Map.class));
        assertStatus(HttpStatus.NOT_FOUND, rest.getForEntity(path, Map.class));


    }

    @Test
    void unreadableHistoryRemainsDistinctFromAnAbsentVariant() {
        jdbc.update("UPDATE air_ws_items_index SET source_revision = 2 WHERE branch_id = ?", childTracker);
        Difference difference = diff(compare("1", child), anchor);
        assertEquals(Presence.PRESENT, difference.source().presence());
        assertEquals(Presence.UNREADABLE, difference.target().presence());
        assertEquals("HISTORY_UNAVAILABLE", difference.target().failure());
        assertNotNull(difference.target().localId());
    }

    private Derived derive(String target, String tracker, String source, String family) {
        DeriveFixture.Result result = DeriveFixture.derive(jdbc, target, tracker, source, 3, Long.valueOf(family));
        return new Derived(result.trackerId(), result.sourceTrackerId(), result.item());
    }

    private void createChild(String parent, String next) {
        FixtureIds.insertWorkingSet(jdbc, parent, next);
        worksets.add(next);
    }

    private void mountSpaceWithLocalFolder(String workingSetId) {
        String folderId = FixtureIds.ensureDefaultTracker(jdbc, workingSetId, space);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id)
                VALUES (?, ?, ?)
                """, workingSetId, folderId, space);
    }

    private String seed(String tracker, String common, String title) {
        String local = fixtureId("L-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO air_inst_requirement_rev (instance_id, common_id, tracker, name, description, revision, lock_version,
                    workingset_id, created_by, created_time)
                VALUES (?, ?, ?, ?, 'description', 3, 0,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, local, common, tracker, title, tracker, System.currentTimeMillis());
        String owner = jdbc.queryForObject("SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?",
                String.class, tracker);
        ItemIndexFixture.insert(jdbc, local, common, tracker, owner, 3, local, 3, true);
        return local;
    }

    private int confirmations(String round, String family) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM air_ws_items_merge_index WHERE batch_id = ? AND common_id = ?
                """, Integer.class, Long.valueOf(round), Long.valueOf(family));
    }

    private RequirementView requirement(String local) {
        return rest.getForObject("/api/requirements/" + local, RequirementView.class);
    }

    private void change(String local, String title, int lock) {
        assertStatus(HttpStatus.OK, rest.exchange("/api/requirements/" + local + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", title, "expectedLockVersion", lock)), RequirementView.class));
    }

    private ComparisonPage compare(String source, String target) {
        return "1".equals(source) ? compare(source, target, rootTracker, childTracker)
                : compare(source, target, childTracker, rootTracker);
    }

    private String comparePath(String source, String target, String sourceTracker, String targetTracker) {
        return ws(target) + "/compare/" + source + "?sourceTrackerId=" + sourceTracker
                + "&targetTrackerId=" + targetTracker;
    }

    private ComparisonPage compare(String source, String target, String sourceTracker, String targetTracker) {
        ResponseEntity<ComparisonPage> response = rest.postForEntity(comparePath(source, target, sourceTracker, targetTracker), null, ComparisonPage.class);
        assertStatus(HttpStatus.OK, response);
        return response.getBody();
    }

    @SafeVarargs
    private final String submit(String target, ComparisonPage compared, Map<String, Object>... entries) {
        ResponseEntity<MergeRound> response = rest.postForEntity(ws(target) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", Long.toString(compared.source()),
                "requestId", UUID.randomUUID().toString(),
                "decisions", List.of(entries)), MergeRound.class);
        assertStatus(HttpStatus.CREATED, response);
        String id = response.getBody().id();
        rounds.add(id);
        tokens.add(response.getBody().token());
        return id;
    }

    private MergeRound roundState(String round) {
        return rest.getForObject(roundPath(round), MergeRound.class);
    }

    private ApplyResult awaitResult(String round) {
        await().atMost(Duration.ofSeconds(10)).until(() -> roundState(round).result() != null);
        return roundState(round).result();
    }

    @SafeVarargs
    private final boolean submitFails(String target, ComparisonPage compared, Map<String, Object>... entries) {
        ResponseEntity<Map> response = rest.postForEntity(ws(target) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", Long.toString(compared.source()),
                "requestId", UUID.randomUUID().toString(),
                "decisions", List.of(entries)), Map.class);
        return response.getStatusCode().is4xxClientError();
    }

    @SafeVarargs
    private final String submitFailure(String target, ComparisonPage compared, Map<String, Object>... entries) {
        ResponseEntity<Map> response = rest.postForEntity(ws(target) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", Long.toString(compared.source()),
                "requestId", UUID.randomUUID().toString(),
                "decisions", List.of(entries)), Map.class);
        assertTrue(response.getStatusCode().is4xxClientError(), () -> String.valueOf(response.getBody()));
        return String.valueOf(response.getBody().get("reason"));
    }

    private ApplyResult apply(String round) {
        ResponseEntity<MergeRound> response = rest.postForEntity(roundPath(round) + "/apply", null, MergeRound.class);
        assertTrue(response.getStatusCode() == HttpStatus.ACCEPTED || response.getStatusCode() == HttpStatus.OK,
                String.valueOf(response.getBody()));
        return awaitResult(round);
    }

    private String id(String prefix) {
        return fixtureId(prefix + "-" + suffix);
    }

    private record Derived(String trackerId, String sourceTrackerId, LightTrackerItem item) {
    }
}
