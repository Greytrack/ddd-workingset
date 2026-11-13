package bootstrap;

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

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.infrastructure.JdbcRequirementRepository;
import bootstrap.web.DeriveView;
import bootstrap.web.MergeController.ComparisonPage;
import bootstrap.web.RequirementView;
import bootstrap.web.WorkingSetController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.application.MergeExecutionService.Registration;
import sk.workingset.application.port.MergeStore;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;
import sk.workingset.domain.merge.MergeRound.ApplyResult;
import sk.workingset.domain.merge.MergeRound.Outcome;
import sk.workingset.domain.merge.MergeRound.State;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MergeAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private MergeStore store;
    @Autowired private RequirementApplicationService requirements;
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
        var source = seed(rootTracker, anchor, "same");
        childTracker = derive(child, rootTracker, source, anchor).trackerId();
    }

    @AfterEach
    void cleanup() {
        for (String round : rounds) {
            jdbc.update("DELETE FROM merge_rounds WHERE id = ?", round);
        }
        for (String token : tokens) {
            jdbc.update("DELETE FROM merge_checkpoints WHERE token = ?", token);
            jdbc.update("DELETE FROM merge_comparisons WHERE token = ?", token);
        }
        var itemIds = jdbc.queryForList("""
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
            redis.delete("workingset:lti:" + local);
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

        var compared = compare(source, target);
        assertEquals(Presence.ABSENT, diff(compared, add).target().presence());
        assertEquals(Presence.ABSENT, diff(compared, remove).source().presence());
        var round = start(target, compared);
        register(round, existence(diff(compared, add), "CREATE"), existence(diff(compared, remove), "DELETE"),
                field(diff(compared, edit), "ACCEPT", false));
        var result = apply(round);
        assertEquals(State.Succeeded, result.state());
        assertEquals(3, result.summary().applied());
        assertEquals("source", requirement(editedId).title());
        assertStatus(HttpStatus.GONE, rest.getForEntity("/api/requirements/" + removedId, Map.class));
        assertTrue(jdbc.queryForObject("SELECT delete_flag FROM requirements WHERE local_id = ?", Boolean.class, removedId));
        assertEquals("new", jdbc.queryForObject(
                "SELECT title FROM requirements WHERE tracker_id = ? AND common_id = ?", String.class, targetTracker, add));
        assertEquals(result, apply(round));
        var reused = rest.postForEntity(ws(target) + "/merge-rounds",
                Map.of("comparisonToken", compared.comparisonToken()), Map.class);
        assertStatus(HttpStatus.CONFLICT, reused);
        assertEquals("ComparisonAlreadyUsed", reused.getBody().get("reason"));

        seed(sourceTracker, remove, "present again");
        var restoreComparison = compare(source, target);
        assertEquals(Presence.DELETED, diff(restoreComparison, remove).target().presence());
        String restoreRound = start(target, restoreComparison);
        register(restoreRound, existence(diff(restoreComparison, remove), "RESTORE"));
        assertEquals(State.Succeeded, apply(restoreRound).state());
        assertEquals("to delete", requirement(removedId).title());
    }

    @Test
    void registrationRejectsChangedEvidenceThenExecutionUsesCurrentFieldsAndGuardsRevision() {
        String edit = id("edit");
        String source = seed(rootTracker, edit, "source");
        String target = seed(childTracker, edit, "target");
        var frozen = compare("1", child);
        String staleRound = start(child, frozen);
        change(source, "changed before registration", 0);
        var frozenPage = rest.getForObject("/api/comparisons/" + frozen.comparisonToken(), ComparisonPage.class);
        assertEquals("source", diff(frozenPage, edit).source().fields().get("title"));
        assertEquals("StaleEvidence", register(staleRound, field(diff(frozen, edit), "ACCEPT", false))[0].failure().name());

        var fresh = compare("1", child);
        String round = start(child, fresh);
        register(round, field(diff(fresh, edit), "ACCEPT", false));
        change(source, "latest source", 1);
        change(target, "latest target", 0);
        assertEquals(State.Succeeded, apply(round).state());
        assertEquals("latest source", requirement(target).title());

        change(source, "different", 2);
        var beforeRevision = compare("1", child);
        String revisionRound = start(child, beforeRevision);
        register(revisionRound, field(diff(beforeRevision, edit), "ACCEPT", false));
        requirements.upgradeRevision(LocalId.of(target), LockVersion.of(2));
        assertEquals(Outcome.StaleEvidence, apply(revisionRound).itemResults().getFirst().outcome());
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
        var comparison = compare("1", child);
        String round = start(child, comparison);
        assertEquals("InvalidDecision",
                register(round, field(diff(comparison, deferred), "DEFER", true))[0].failure().name());
        register(round, field(diff(comparison, keep), "KEEP_TARGET", true),
                field(diff(comparison, rejected), "ACCEPT", false), field(diff(comparison, deferred), "DEFER", false));
        jdbc.update("UPDATE requirements SET title = '', lock_version = 1 WHERE local_id = ?", rejectedSource);
        var partial = apply(round);
        assertEquals(State.PartiallySucceeded, partial.state());
        assertEquals(List.of(Outcome.Confirmed, Outcome.Rejected, Outcome.Deferred),
                partial.itemResults().stream().map(MergeRound.ItemResult::outcome).toList());
        assertEquals(0, requirement(kept).lockVersion());
        assertEquals("target", requirement(rejectedTarget).title());

        var key = new MergeCheckpoint.Key(WorkingSetId.of("1"), WorkingSetId.of(child), CommonId.of(keep));
        var checkpoint = store.findCheckpoint(key).orElseThrow();
        change(rejectedSource, "valid on retry", 1);
        var retryComparison = compare("1", child);
        assertFalse(retryComparison.differences().stream().anyMatch(item -> keep.equals(item.family().value())));
        String retryRound = start(child, retryComparison);
        register(retryRound, field(diff(retryComparison, rejected), "ACCEPT", false));
        assertEquals(1, apply(retryRound).summary().applied());
        assertEquals("valid on retry", requirement(rejectedTarget).title());
        assertEquals(partial, rest.getForObject(roundPath(round), MergeRound.class).result());

        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(ws(child) + "/merge-checkpoints/1/" + keep + "/revoke",
                Map.of("expectedDecisionVersion", checkpoint.decisionVersion()), Void.class));
        assertFalse(store.recordCheckpoint(checkpoint));
        assertTrue(store.findCheckpoint(key).orElseThrow().revoked());
        assertNotNull(diff(compare("1", child), keep));
    }

    @Test
    void unmaterializedTargetIsComparedAndWrittenByAlmBeforeSwitchingLocal() {
        String grandchild = id("grandchild");
        createChild(child, grandchild);
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(grandchild) + "/spaces/" + space, null, Void.class));
        String parentItem = rest.getForObject(ws(child) + "/bindings",
                WorkingSetController.BindingsView.class).trackers().getFirst().trackerId();
        String parentLocal = jdbc.queryForObject("SELECT local_id FROM light_tracker_items WHERE tracker_id = ?",
                String.class, parentItem);
        var grand = derive(grandchild, childTracker, parentLocal, anchor);

        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version)
                VALUES (?, ?, ?, 'parent local', 'description', 3, 0)
                """, parentLocal, anchor, childTracker);
        jdbc.update("UPDATE light_tracker_items SET materialized = TRUE, source_local_id = local_id WHERE local_id = ?",
                parentLocal);
        redis.delete("workingset:lti:" + parentLocal);

        var compared = compare(child, grandchild);
        var item = diff(compared, anchor);
        assertEquals(grand.item().localId(), item.target().localId().value());
        assertFalse(item.target().localId().equals(item.target().contentId()));
        String round = start(grandchild, compared);
        register(round, field(item, "ACCEPT", false));
        assertEquals(State.Succeeded, apply(round).state());
        assertEquals("parent local", requirement(grand.item().localId()).title());
        assertTrue(jdbc.queryForObject("SELECT materialized FROM light_tracker_items WHERE local_id = ?",
                Boolean.class, grand.item().localId()));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity("/api/requirements/" + grand.item().localId()
                + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity(ws(grandchild) + "/items/" + grand.item().localId(), Map.class));
    }

    @Test
    void rejectsSiblingAcrossGenerationAndOutOfScopeTrackerAndReturnsEmptyCandidates() {
        String sibling = id("sibling");
        String grandchild = id("grandchild");
        createChild("1", sibling);
        createChild(child, grandchild);
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(child) + "/compare/" + sibling, null, Map.class));
        assertStatus(HttpStatus.CONFLICT, rest.postForEntity(ws(grandchild) + "/compare/1", null, Map.class));
        String shared = id("shared");
        jdbc.update("INSERT INTO classifications VALUES (?, 'SHARED')", sharedClassification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'SHARED')
                """, shared, space, sharedClassification);
        assertStatus(HttpStatus.CREATED, rest.postForEntity(ws(child) + "/trackers/shared/" + shared, null, Void.class));
        assertStatus(HttpStatus.CONFLICT,
                rest.postForEntity(ws(child) + "/compare/1?trackerId=" + shared, null, Map.class));

        var empty = compare(child, grandchild);
        assertTrue(empty.candidates().isEmpty());
        assertTrue(empty.differences().isEmpty());
        assertEquals(State.Failed, apply(start(grandchild, empty)).state());
    }

    @Test
    void publicDeleteUndeleteAndDelete2DistinguishMarkedAndPermanentlyRemovedItems() {
        String local = seed(rootTracker, id("life"), "lifecycle");
        String path = "/api/requirements/" + local;
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class));
        assertStatus(HttpStatus.GONE, rest.getForEntity(path, Map.class));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/undelete", null, Void.class));
        assertEquals("lifecycle", requirement(local).title());
        var staleUpdate = repository.findById(LocalId.of(local)).orElseThrow().changeTitle("stale", LockVersion.of(2));
        assertStatus(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete2", null, Void.class));
        assertThrows(sk.workingset.application.ApplicationFailure.class, () -> repository.save(staleUpdate));
        assertStatus(HttpStatus.NOT_FOUND, rest.postForEntity(path + "/undelete", null, Map.class));
        assertStatus(HttpStatus.NOT_FOUND, rest.getForEntity(path, Map.class));

        String inherited = jdbc.queryForObject("SELECT local_id FROM requirements WHERE common_id = ? AND tracker_id = ?",
                String.class, anchor, rootTracker);
        assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                rest.postForEntity("/api/requirements/" + inherited + "/delete2", null, Map.class));
        assertStatus(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + inherited + "/delete", null, Void.class));
        String dependent = jdbc.queryForObject("SELECT local_id FROM light_tracker_items WHERE tracker_id = ?",
                String.class, childTracker);
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT materialized FROM light_tracker_items WHERE local_id = ?", Boolean.class, dependent)));
        assertStatus(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + inherited + "/delete2", null, Void.class));
        assertEquals("same", requirement(dependent).title());
    }

    @Test
    void unreadableHistoryRemainsDistinctFromAnAbsentVariant() {
        jdbc.update("UPDATE light_tracker_items SET source_revision = 2 WHERE tracker_id = ?", childTracker);
        var difference = diff(compare("1", child), anchor);
        assertEquals(Presence.PRESENT, difference.source().presence());
        assertEquals(Presence.UNREADABLE, difference.target().presence());
        assertEquals("HISTORY_UNAVAILABLE", difference.target().failure());
        assertNotNull(difference.target().localId());
    }

    private DeriveView derive(String target, String tracker, String source, String family) {
        var response = rest.postForEntity(ws(target) + "/derive", Map.of("sourceTrackerId", tracker,
                "sourceLocalId", source, "sourceRevision", 3, "commonId", family), DeriveView.class);
        assertStatus(HttpStatus.CREATED, response);
        return response.getBody();
    }

    private void createChild(String parent, String next) {
        assertStatus(HttpStatus.CREATED,
                rest.postForEntity(ws(parent) + "/children", Map.of("workingSetId", next), Void.class));
        worksets.add(next);
    }

    private String seed(String tracker, String common, String title) {
        String local = "L-" + UUID.randomUUID();
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
        var response = rest.postForEntity(ws(target) + "/compare/" + source, null, ComparisonPage.class);
        assertStatus(HttpStatus.OK, response);
        tokens.add(response.getBody().comparisonToken());
        return response.getBody();
    }

    private String start(String target, ComparisonPage compared) {
        var response = rest.postForEntity(ws(target) + "/merge-rounds",
                Map.of("comparisonToken", compared.comparisonToken()), MergeRound.class);
        assertStatus(HttpStatus.CREATED, response);
        String id = response.getBody().id();
        rounds.add(id);
        return id;
    }

    @SafeVarargs
    private final Registration[] register(String round, Map<String, Object>... entries) {
        var response = rest.postForEntity(roundPath(round) + "/entries", Map.of("entries", List.of(entries)),
                Registration[].class);
        assertStatus(HttpStatus.OK, response);
        return response.getBody();
    }

    private ApplyResult apply(String round) {
        var response = rest.postForEntity(roundPath(round) + "/apply", null, ApplyResult.class);
        assertStatus(HttpStatus.OK, response);
        return response.getBody();
    }

    private static Map<String, Object> existence(Difference diff, String action) {
        return Map.of("entryKey", diff.key(), "decision", Map.of("existence", action, "markAsMerged", false));
    }

    private static Map<String, Object> field(Difference diff, String action, boolean mark) {
        return Map.of("entryKey", diff.key(), "decision", Map.of("fields", Map.of("title", action), "markAsMerged", mark));
    }

    private static Difference diff(ComparisonPage page, String family) {
        return page.differences().stream().filter(item -> family.equals(item.family().value())).findFirst().orElseThrow();
    }

    private String id(String prefix) { return prefix + "-" + suffix; }
    private static String ws(String id) { return "/api/worksets/" + id; }
    private static String roundPath(String id) { return "/api/merge-rounds/" + id; }

    private static void assertStatus(HttpStatus status, ResponseEntity<?> response) {
        assertEquals(status, response.getStatusCode(), () -> String.valueOf(response.getBody()));
    }
}
