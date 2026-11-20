package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import alm.requirement.application.command.RequirementCommandService;
import alm.requirement.infrastructure.repositories.JdbcRequirementRepository;
import sk.workingset.adapters.LightTrackerItemView;
import sk.workingset.adapters.MergeController.ComparisonPage;
import alm.requirement.adapters.RequirementView;
import sk.workingset.adapters.WorkingSetController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.domain.merge.MergeCheckpointCleanupStore;
import sk.workingset.domain.merge.MergeCheckpointRepository;
import sk.workingset.domain.merge.MergeDecisionVersionGenerator;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.merge.Comparison.ChangeKind;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.Comparison.TrackerPair;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.ApplyResult;
import sk.workingset.domain.merge.MergeRound.Outcome;
import sk.workingset.domain.merge.MergeRound.State;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MergeAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @SpyBean private MergeCheckpointRepository store;
    @Autowired private MergeCheckpointCleanupStore checkpointCleanup;
    @Autowired private MergeDecisionVersionGenerator decisionVersions;
    @Autowired private RequirementCommandService requirements;
    @Autowired private JdbcRequirementRepository repository;

    private final String suffix = UUID.randomUUID().toString();
    private final String child = id("child");
    private final String space = id("space");
    private final String classification = id("class");
    private final String rootTracker = id("root");
    private final String sharedClassification = id("shared-class");
    private final String anchor = id("anchor");
    private final List<String> tokens = new ArrayList<>();
    private final List<String> rounds = new ArrayList<>();
    private final List<String> worksets = new ArrayList<>();
    private String childTracker;
    private String childAnchor;

    @BeforeEach
    void setup() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        jdbc.update("INSERT INTO classifications VALUES (?, 'UNSHARED')", classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, rootTracker, space, classification);
        createChild("1", child);
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(child) + "/spaces/" + space, null, Void.class));
        String source = seed(rootTracker, anchor, "same");
        MergeAcceptanceTest.Derived derived = derive(child, rootTracker, source, anchor);
        childTracker = derived.trackerId();
        childAnchor = derived.item().localId();
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
        jdbc.update("DELETE FROM merge_checkpoints WHERE round_id = ?", id("cleanup-round"));
        jdbc.update("DELETE FROM merge_rounds WHERE request_id LIKE ?", "%" + suffix + "%");
        for (String round : rounds) {
            jdbc.update("DELETE FROM merge_checkpoints WHERE round_id = ?", round);
            jdbc.update("DELETE FROM merge_rounds WHERE id = ?", round);
        }
        List<String> itemIds = jdbc.queryForList("""
                SELECT local_id FROM requirements WHERE tracker_id IN
                    (SELECT tracker_id FROM trackers WHERE product_space_id = ?)
                UNION SELECT local_id FROM light_tracker_items WHERE tracker_id IN
                    (SELECT tracker_id FROM trackers WHERE product_space_id = ?)
                """, String.class, space, space);
        for (String local : itemIds) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + local + "%");
            jdbc.update("DELETE FROM cow_failures WHERE source_local_id = ?", local);
            jdbc.update("DELETE FROM cow_materializations WHERE source_local_id = ?", local);
            jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", local);
            jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", local);
            jdbc.update("DELETE FROM requirements WHERE local_id = ?", local);
        }
        for (String workset : worksets.reversed()) {
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", workset);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", workset);
        }
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM classifications WHERE classification_id IN (?, ?)", classification, sharedClassification);
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
        assertEquals(WorkingSetId.of(source), diff(compared, add).trackers().sourceWorkingSetId());
        assertEquals(WorkingSetId.of(target), diff(compared, add).trackers().targetWorkingSetId());
        assertTrue(diff(compared, add).fields().isEmpty());
        assertTrue(diff(compared, remove).fields().isEmpty());
        assertTrue(diff(compared, edit).fields().contains("title"));
        assertFalse(compared.differences().stream().anyMatch(item -> anchor.equals(item.family().value())));
        String round = submit(target, compared, existence(diff(compared, add), "CREATE"),
                existence(diff(compared, remove), "DELETE"), field(diff(compared, edit), "ACCEPT", false));
        MergeRound.ApplyResult result = awaitResult(round);
        assertEquals(State.Succeeded, result.state());
        assertEquals(3, result.summary().applied());
        assertEquals("source", requirement(editedId).title());
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + removedId, Map.class));
        assertTrue(jdbc.queryForObject("SELECT delete_flag FROM requirements WHERE local_id = ?", Boolean.class, removedId));
        assertEquals("new", jdbc.queryForObject(
                "SELECT title FROM requirements WHERE tracker_id = ? AND common_id = ?", String.class, targetTracker, add));
        assertEquals(result, apply(round));

        // C11：软删对象不进入 Compare 候选，恢复走 BC 的 undelete，不再通过 Merge RESTORE。
        seed(sourceTracker, remove, "present again");
        ComparisonPage afterDelete = compare(source, target);
        assertEquals(Presence.ABSENT, diff(afterDelete, remove).target().presence());
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + removedId, Map.class));
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
        Map<String,Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
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
                "SELECT count(*) FROM merge_rounds WHERE request_id = ?", Integer.class, requestId));
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
        List<Map<String,Object>> oversized = java.util.stream.IntStream.range(0, 51)
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
        Map<String,Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
                "decisions", List.of(existence(difference, "CREATE")));
        CountDownLatch start = new CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<ResponseEntity<Map>> one = executor.submit(() -> {
                start.await();
                return rest.postForEntity(ws(child) + "/merge-rounds", body, Map.class);
            });
            java.util.concurrent.Future<ResponseEntity<Map>> two = executor.submit(() -> {
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
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM merge_rounds WHERE request_id = ?",
                Integer.class, requestId));
    }

    @Test
    void failedSubmissionLeavesNoRoundBehind() {
        String family = id("failed-submit");
        String source = seed(rootTracker, family, "failed submit probe");
        ComparisonPage frozen = compare("1", child);
        change(source, "changed before submit", 0);
        String requestId = id("failed-submit-request");
        Map<String,Object> body = Map.of("sourceWorkingSetId", "1", "requestId", requestId,
                "decisions", List.of(field(diff(frozen, family), "ACCEPT", false)));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(child) + "/merge-rounds", body, Map.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM merge_rounds WHERE request_id = ?",
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
                "requestId", requestId, "decisions", List.of(existence(difference, "CREATE"))), MergeRound.class);
        assertStatus(HttpStatus.CREATED, accepted);
        rounds.add(accepted.getBody().id());
        tokens.add(accepted.getBody().token());
        ResponseEntity<Map> conflict = rest.postForEntity(ws("1") + "/merge-rounds", Map.of("sourceWorkingSetId", child,
                "requestId", requestId, "decisions", List.of(existence(difference, "CREATE"))), Map.class);
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
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(grandchild) + "/spaces/" + space, null, Void.class));
        String grandTracker = derive(grandchild, childTracker, childAnchor, anchor).trackerId();
        assertNativeMergeBack(child, grandchild, childTracker, grandTracker);
    }

    private void assertNativeMergeBack(String parent, String sourceWs, String parentTracker, String sourceTracker) {
        RequirementView source = createRequirement(sourceWs, sourceTracker, "native child item");
        ComparisonPage comparison = compare(sourceWs, parent, sourceTracker, parentTracker);
        Difference difference = diff(comparison, source.commonId());
        assertEquals(Presence.ABSENT, difference.target().presence());
        String round = submit(parent, comparison, existence(difference, "CREATE"));
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
        if (!"1".equals(parent)) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertStatus(HttpStatus.OK,
                    rest.getForEntity(ws(parent) + "/items/" + parentLocal, Map.class)));
        }

        change(source.localId(), "updated child", 0);
        ComparisonPage active = compare(sourceWs, parent, sourceTracker, parentTracker);
        assertEquals(Presence.PRESENT, diff(active, source.commonId()).target().presence());
        assertTrue(submitFails(parent, active, existence(diff(active, source.commonId()), "CREATE")));

        assertStatus(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + parentLocal + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + parentLocal, Map.class));
        ComparisonPage afterDelete = compare(sourceWs, parent, sourceTracker, parentTracker);
        Difference absent = diff(afterDelete, source.commonId());
        assertEquals(Presence.ABSENT, absent.target().presence());
        assertTrue(submitFails(parent, afterDelete, existence(absent, "RESTORE")));
        ComparisonPage fresh = compare(sourceWs, parent, sourceTracker, parentTracker);
        String restoreRound = submit(parent, fresh, existence(diff(fresh, source.commonId()), "CREATE"));
        MergeRound.ApplyResult restored = awaitResult(restoreRound);
        assertEquals(State.Succeeded, restored.state(), restored::toString);
        assertEquals(parentLocal, restored.itemResults().getFirst().businessRef());
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
        String firstRound = submit("1", first, existence(diff(first, source.commonId()), "CREATE"));
        assertEquals(Outcome.Applied, awaitResult(firstRound).itemResults().getFirst().outcome());
        assertEquals("StaleEvidence", submitFailure("1", stale, existence(diff(stale, source.commonId()), "CREATE")));
        assertEquals(source.title(), requirement(source.commonId()).title());
    }

    private RequirementView createRequirement(String workset, String tracker, String title) {
        ResponseEntity<RequirementView> response = rest.postForEntity("/api/requirements", Map.of("workingSetId", workset,
                "trackerId", tracker, "title", title, "description", "description", "revision", 3),
                RequirementView.class);
        assertStatus(HttpStatus.CREATED, response);
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM light_tracker_items WHERE local_id = ?)",
                Boolean.class, response.getBody().localId())));
        return response.getBody();
    }

    @Test
    void registrationRejectsChangedEvidenceThenExecutionUsesCurrentFieldsAndGuardsRevision() {
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
        requirements.upgradeRevision(LocalId.of(target), LockVersion.of(1));
        assertEquals("StaleEvidence",
                submitFailure(child, beforeRevision, field(diff(beforeRevision, edit), "ACCEPT", false)));
        assertEquals("latest source", requirement(target).title());
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
        assertEquals("InvalidDecision",
                submitFailure(child, comparison, field(diff(comparison, deferred), "DEFER", true)));
        jdbc.update("UPDATE requirements SET title = '', lock_version = 1 WHERE local_id = ?", rejectedSource);
        ComparisonPage withInvalidSource = compare("1", child);
        String round = submit(child, withInvalidSource, field(diff(withInvalidSource, keep), "KEEP_TARGET", true),
                field(diff(withInvalidSource, rejected), "ACCEPT", false),
                field(diff(withInvalidSource, deferred), "DEFER", false));
        MergeRound.ApplyResult partial = awaitResult(round);
        assertEquals(State.PartiallySucceeded, partial.state());
        assertEquals(List.of(Outcome.Confirmed, Outcome.Rejected, Outcome.Deferred),
                partial.itemResults().stream().map(MergeRound.ItemResult::outcome).toList());
        assertEquals(0, requirement(kept).lockVersion());
        assertEquals("target", requirement(rejectedTarget).title());

        MergeCheckpoint.Key key = new MergeCheckpoint.Key(TrackerId.of(rootTracker), TrackerId.of(childTracker),
                CommonId.of(keep));
        MergeCheckpoint checkpoint = store.findById(key).orElseThrow();
        assertNotNull(checkpoint.localIdA());
        assertNotNull(checkpoint.localIdB());
        assertTrue(kept.equals(checkpoint.localIdA().value()) || kept.equals(checkpoint.localIdB().value()));
        change(kept, "changed after checkpoint", 0);
        ComparisonPage afterCheckpointEdit = compare("1", child);
        assertFalse(afterCheckpointEdit.differences().stream().anyMatch(item -> keep.equals(item.family().value())));
        change(rejectedSource, "valid on retry", 1);
        ComparisonPage retryComparison = compare("1", child);
        assertFalse(retryComparison.differences().stream().anyMatch(item -> keep.equals(item.family().value())));
        String retryRound = submit(child, retryComparison, field(diff(retryComparison, rejected), "ACCEPT", false));
        assertEquals(1, awaitResult(retryRound).summary().applied());
        assertEquals("valid on retry", requirement(rejectedTarget).title());
        assertEquals(partial, rest.getForObject(roundPath(round), MergeRound.class).result());

        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(
                "/api/merge-checkpoints/" + key.a().value() + "/" + key.b().value() + "/" + keep + "/revoke",
                Map.of("expectedDecisionVersion", checkpoint.decisionVersion()), Void.class));
        assertThrows(sk.workingset.domain.merge.MergeFailure.class, () -> store.save(checkpoint));
        assertTrue(store.findById(key).orElseThrow().revoked());
        assertNotNull(diff(compare("1", child), keep));
    }

    @Test
    void repeatedApplyIsIdempotentAndRecordsOnlyTheCommittedWrite() {
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

        MergeCheckpoint checkpoint = store.findById(new MergeCheckpoint.Key(
                TrackerId.of(rootTracker), TrackerId.of(childTracker), CommonId.of(family))).orElseThrow();
        assertTrue(checkpoint.revisionA().value() == 3 || checkpoint.revisionB().value() == 3);
    }

    private int pendingMergePublications(String localId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE '%RequirementMergeAppliedEvent' AND serialized_event LIKE ?
                AND completion_date IS NULL
                """, Integer.class, "%" + localId + "%");
    }

    @Test
    void unmaterializedTargetIsComparedAndWrittenByAlmBeforeSwitchingLocal() {
        String grandchild = id("grandchild");
        createChild(child, grandchild);
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(grandchild) + "/spaces/" + space, null, Void.class));
        String parentItem = childTracker;
        String parentLocal = jdbc.queryForObject("SELECT local_id FROM light_tracker_items WHERE tracker_id = ?",
                String.class, parentItem);
        MergeAcceptanceTest.Derived grand = derive(grandchild, childTracker, parentLocal, anchor);

        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version)
                VALUES (?, ?, ?, 'parent local', 'description', 3, 0)
                """, parentLocal, anchor, childTracker);
        jdbc.update("UPDATE light_tracker_items SET materialized = TRUE, source_local_id = local_id WHERE local_id = ?",
                parentLocal);

        ComparisonPage compared = compare(child, grandchild, childTracker, grand.trackerId());
        Difference item = diff(compared, anchor);
        assertEquals(grand.item().localId(), item.target().localId().value());
        assertFalse(item.target().localId().equals(item.target().contentId()));
        String round = submit(grandchild, compared, field(item, "ACCEPT", false));
        assertEquals(State.Succeeded, awaitResult(round).state());
        assertEquals("parent local", requirement(grand.item().localId()).title());
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT materialized FROM light_tracker_items WHERE local_id = ?",
                Boolean.class, grand.item().localId())));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity("/api/requirements/" + grand.item().localId()
                + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity(ws(grandchild) + "/items/" + grand.item().localId(), Map.class));
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
        jdbc.update("INSERT INTO classifications VALUES (?, 'SHARED')", sharedClassification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'SHARED')
                """, shared, space, sharedClassification);
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(child) + "/trackers/shared/" + shared, null, Void.class));
        RequirementView sharedItem = createRequirement(child, shared, "shared child entry");
        assertEquals(sharedItem.localId(), sharedItem.commonId());
        assertTrue(compare(child, "1").differences().stream()
                .noneMatch(item -> sharedItem.commonId().equals(item.family().value())));
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
        assertFalse(backward.differences().stream().anyMatch(item -> family.equals(item.family().value())));
    }

    @Test
    void concurrentPairSubmissionIsRejected() {
        String family = id("pair-busy");
        seed(rootTracker, family, "source");
        Difference difference = diff(compare("1", child), family);
        String low = rootTracker.compareTo(childTracker) < 0 ? rootTracker : childTracker;
        String high = low.equals(rootTracker) ? childTracker : rootTracker;
        jdbc.update("""
                INSERT INTO merge_rounds (id, token, request_id, state, tracker_a_id, tracker_b_id)
                VALUES (?, ?, ?, CAST('{"state":"Running"}' AS jsonb), ?, ?)
                """, id("busy-round"), id("busy-token"), id("busy-request"), low, high);
        rounds.add(id("busy-round"));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("pair-request"),
                "decisions", List.of(existence(difference, "CREATE"))), Map.class));
    }

    @Test
    void pairCanMergeAgainAfterTerminal() {
        String family = id("pair-again");
        seed(rootTracker, family, "source");
        Difference first = diff(compare("1", child), family);
        assertEquals(State.Succeeded, awaitResult(submit(child, compare("1", child),
                existence(first, "CREATE"))).state());
        String sourceLocal = jdbc.queryForObject(
                "SELECT local_id FROM requirements WHERE tracker_id = ? AND common_id = ?",
                String.class, rootTracker, family);
        change(sourceLocal, "changed after first round", 0);
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
                new sk.workingset.domain.merge.Comparison.TrackerPair(WorkingSetId.of("1"),
                        TrackerId.of("265564874633152"), WorkingSetId.of(child), TrackerId.of("264912685293504")),
                difference.source(), difference.target(), difference.fields());
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("mixed-request"),
                "decisions", List.of(existence(difference, "CREATE"), existence(other, "CREATE"))), Map.class));
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
        TrackerPair first = new TrackerPair(WorkingSetId.of("1"), TrackerId.of(rootTracker),
                WorkingSetId.of(child), TrackerId.of(childTracker));
        TrackerPair second = new TrackerPair(WorkingSetId.of("1"), TrackerId.of(secondRoot),
                WorkingSetId.of(child), TrackerId.of(secondChild));

        ComparisonPage page = compare("1", child);
        assertEquals(List.of(first), page.candidates());
        assertEquals(List.of(firstFamily), page.differences().stream().map(d -> d.family().value()).toList());
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
                new sk.workingset.domain.merge.Comparison.TrackerPair(WorkingSetId.of(child),
                        difference.trackers().source(), WorkingSetId.of("1"), difference.trackers().target()),
                difference.source(), difference.target(), difference.fields());
        assertStatus(HttpStatus.BAD_REQUEST, rest.postForEntity(ws(child) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", "1", "requestId", id("mixed-ws-request"),
                "decisions", List.of(existence(difference, "CREATE"), existence(other, "CREATE"))), Map.class));
    }

    @Test
    void supersededCheckpointsAreCleanedUp() {
        String oldFamily = id("cleanup-old");
        String keepFamily = id("cleanup-keep");
        String low = rootTracker.compareTo(childTracker) < 0 ? rootTracker : childTracker;
        String high = low.equals(rootTracker) ? childTracker : rootTracker;
        insertCheckpoint(low, high, oldFamily, 2, 6);
        insertCheckpoint(low, high, keepFamily, 5, 8);
        insertItem(low, oldFamily, 3, low.equals(rootTracker) ? "1" : child);
        insertItem(high, oldFamily, 7, high.equals(rootTracker) ? "1" : child);
        insertItem(low, keepFamily, 5, low.equals(rootTracker) ? "1" : child);
        insertItem(high, keepFamily, 8, high.equals(rootTracker) ? "1" : child);

        assertEquals(1, checkpointCleanup.deleteSupersededCheckpoints());
        MergeCheckpoint.Key oldKey = new MergeCheckpoint.Key(TrackerId.of(low), TrackerId.of(high), CommonId.of(oldFamily));
        MergeCheckpoint.Key keepKey = new MergeCheckpoint.Key(TrackerId.of(low), TrackerId.of(high), CommonId.of(keepFamily));
        assertTrue(store.findById(oldKey).isEmpty());
        assertTrue(store.findById(keepKey).isPresent());
    }

    @Test
    void publicDeleteUndeleteAndDelete2DistinguishMarkedAndPermanentlyRemovedItems() {
        String local = seed(rootTracker, id("life"), "lifecycle");
        String path = "/api/requirements/" + local;
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity(path, Map.class));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/undelete", null, Void.class));
        assertEquals("lifecycle", requirement(local).title());
        alm.requirement.domain.Requirement staleUpdate = repository.findById(LocalId.of(local)).orElseThrow().changeTitle("stale", LockVersion.of(2));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete2", null, Void.class));
        assertThrows(sk.common.error.DomainException.class, () -> repository.save(staleUpdate));
        assertStatus(HttpStatus.NOT_FOUND, rest.postForEntity(path + "/undelete", null, Map.class));
        assertStatus(HttpStatus.NOT_FOUND, rest.getForEntity(path, Map.class));

        String inherited = jdbc.queryForObject("SELECT local_id FROM requirements WHERE common_id = ? AND tracker_id = ?",
                String.class, anchor, rootTracker);
        // 2026-11-18 精简后永久删除不再由 SK 做 COW 依赖前置校验，delete2 直接成功。
        assertStatus(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + inherited + "/delete2", null, Void.class));
    }

    @Test
    void unreadableHistoryRemainsDistinctFromAnAbsentVariant() {
        jdbc.update("UPDATE light_tracker_items SET source_revision = 2 WHERE tracker_id = ?", childTracker);
        Difference difference = diff(compare("1", child), anchor);
        assertEquals(Presence.PRESENT, difference.source().presence());
        assertEquals(Presence.UNREADABLE, difference.target().presence());
        assertEquals("HISTORY_UNAVAILABLE", difference.target().failure());
        assertNotNull(difference.target().localId());
    }

    private Derived derive(String target, String tracker, String source, String family) {
        DeriveFixture.Result result = DeriveFixture.derive(jdbc, target, tracker, source, Revision.of(3), CommonId.of(family));
        return new Derived(result.trackerId(), result.sourceTrackerId(), LightTrackerItemView.from(result.item()));
    }

    private record Derived(String trackerId, String sourceTrackerId, LightTrackerItemView item) {}

    private void createChild(String parent, String next) {
        FixtureIds.insertWorkingSet(jdbc, parent, next);
        worksets.add(next);
    }

    private void insertCheckpoint(String trackerA, String trackerB, String family, int revisionA, int revisionB) {
        jdbc.update("""
                INSERT INTO merge_checkpoints (checkpoint_id, round_id, tracker_a_id, tracker_b_id, common_id,
                    local_id_a, local_id_b, revision_a, revision_b, change_kind, merge_direction, decision_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MODIFIED', 'A_TO_B', ?)
                """, fixtureId("checkpoint-" + family), id("cleanup-round"), trackerA, trackerB, family,
                fixtureId("la-" + family), fixtureId("lb-" + family), revisionA, revisionB, decisionVersions.nextDecisionVersion());
    }

    private void insertItem(String tracker, String family, int revision, String workset) {
        String local = fixtureId("li-" + family + "-" + tracker);
        jdbc.update("""
                INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id,
                    revision, source_local_id, source_revision, materialized)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                """, local, family, tracker, workset, revision, local, revision);
    }

    private String seed(String tracker, String common, String title) {
        String local = fixtureId("L-" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version)
                VALUES (?, ?, ?, ?, 'description', 3, 0)
                """, local, common, tracker, title);
        return local;
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
                "sourceWorkingSetId", compared.source().value(),
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
                "sourceWorkingSetId", compared.source().value(),
                "requestId", UUID.randomUUID().toString(),
                "decisions", List.of(entries)), Map.class);
        return response.getStatusCode().is4xxClientError();
    }

    @SafeVarargs
    private final String submitFailure(String target, ComparisonPage compared, Map<String, Object>... entries) {
        ResponseEntity<Map> response = rest.postForEntity(ws(target) + "/merge-rounds", Map.of(
                "sourceWorkingSetId", compared.source().value(),
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

    private static Map<String, Object> existence(Difference diff, String action) {
        return Map.of("evidence", diff,
                "decision", Map.of("existence", action, "markAsMerged", false));
    }

    private static Map<String, Object> field(Difference diff, String action, boolean mark) {
        return Map.of("evidence", diff,
                "decision", Map.of("fields", Map.of("title", action), "markAsMerged", mark));
    }

    private static Difference diff(ComparisonPage page, String family) {
        return page.differences().stream().filter(item -> family.equals(item.family().value())).findFirst().orElseThrow();
    }

    private String id(String prefix) { return fixtureId(prefix + "-" + suffix); }
    private static String ws(String id) { return "/api/worksets/" + id; }
    private static String roundPath(String id) { return "/api/merge-rounds/" + id; }

    private static void assertStatus(HttpStatus status, ResponseEntity<?> response) {
        assertEquals(status, response.getStatusCode(), () -> String.valueOf(response.getBody()));
    }
}
