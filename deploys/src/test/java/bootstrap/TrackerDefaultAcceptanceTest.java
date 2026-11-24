package bootstrap;


import com.airedgesoft.alm.requirement.adapters.RequirementView;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectProvider;
import com.airedgesoft.sharedkernel.productspace.adapters.ProductSpaceController.ProductSpaceView;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController.BindingsView;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController.ClassificationView;
import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController.TrackerView;
import com.airedgesoft.sharedkernel.workingset.application.query.WorkingSetQueryService;
import com.airedgesoft.sharedkernel.workingset.application.query.model.TrackerBindingSummary;
import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.Classification;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-F e 裁定：每个（工作集，空间，分类）一个默认 Tracker；同空间+分类允许出现多个 Tracker。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackerDefaultAcceptanceTest {
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String child = fixtureId("WS-CHILD-" + suffix);
    private final String sharedClassification = "14010102";
    private final Set<String> testSpaces = new HashSet<>();
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TrackerRepository trackerRepository;
    @Autowired
    private ClassificationQueryProvider classifications;
    @Autowired
    private LiteBusinessObjectProvider instanceSummaries;
    @Autowired
    private WorkingSetQueryService workingSetQueries;
    @Autowired
    private TransactionTemplate transactions;

    @AfterEach
    void cleanup() {
        for (String space : testSpaces) {
            jdbc.update("DELETE FROM air_ws_items_index WHERE branch_id IN (SELECT id FROM air_inst_tracker_rev WHERE work_space = ?)", space);
            jdbc.update("DELETE FROM air_inst_requirement_rev WHERE tracker IN (SELECT id FROM air_inst_tracker_rev WHERE work_space = ?)", space);
            jdbc.update("DELETE FROM air_ws_workingset_branch_relation WHERE work_space = ?", space);
            jdbc.update("DELETE FROM air_ws_tracker WHERE id IN (SELECT id FROM air_inst_tracker_rev WHERE work_space = ?)", space);
            jdbc.update("DELETE FROM air_inst_tracker_rev WHERE work_space = ?", space);
            jdbc.update("DELETE FROM air_inst_product_space WHERE id = ?", space);
        }
        jdbc.update("DELETE FROM air_ws_creations WHERE new_working_set_id = ?", child);
        jdbc.update("DELETE FROM air_inst_workingset WHERE id = ?", child);
    }

    @Test
    void exposesThePresetClassificationTriples() {
        ClassificationView[] metadata = rest.getForObject(
                "/api/worksets/metadata/classifications", ClassificationView[].class);
        List<List<String>> triples = Arrays.stream(metadata).map(row ->
                List.of(row.baseClassId(), row.classId(), row.classificationId())).toList();

        assertTrue(triples.containsAll(List.of(
                List.of("1003", "100301", "10030101"),
                List.of("1007", "100701", "10070101"),
                List.of("1008", "100801", "10080101"),
                List.of("1009", "100901", "10090101"),
                List.of("1401", "140101", "14010101"))));
    }

    @Test
    void readsFolderTrackerBindingsWithClassificationMetadataWithoutFolderColumn() {
        String space = createSpace("1");
        transactions.executeWithoutResult(status -> {
            jdbc.execute("""
                    CREATE TEMP TABLE classifications ON COMMIT DROP AS
                    SELECT classification_id, sharing_mode, name, base_class_id, class_id
                    FROM public.classifications
                    """);

            List<TrackerBindingSummary> bindings = workingSetQueries.findBindings(Long.valueOf("1")).trackers().stream()
                    .filter(binding -> binding.productSpaceId().equals(Long.valueOf(space))).toList();

            TrackerBindingSummary folder = bindings.stream().filter(binding -> binding.classificationId().equals(10070101L))
                    .findFirst().orElseThrow();
            assertTrue(bindings.stream().anyMatch(binding -> !binding.classificationId().equals(10070101L)));
            assertEquals(10070101L, classifications.findClassification("10070101").orElseThrow().numericId());

            jdbc.update("DELETE FROM pg_temp.classifications WHERE classification_id = ?", 10070101L);
            Tracker tracker = trackerRepository.lockTracker(folder.trackerId()).orElseThrow();
            assertEquals(10070101L, tracker.numericClassificationId());
            assertEquals(1007L, tracker.baseClassId());
            assertEquals(100701L, tracker.classId());
            assertEquals(10070101L, jdbc.queryForObject("SELECT type FROM air_inst_tracker_rev WHERE id = ?",
                    Long.class, folder.trackerId()));
        });
    }

    @Test
    void creatingProductSpacePublishesDefaultTrackerBindingsAndItemReadAccess() {
        String spaceId = createSpace("1");
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) > 1 AND count(*) = count(DISTINCT xmin::text)
                FROM air_inst_tracker_rev WHERE work_space = ? AND is_default = 1
                """, Boolean.class, Long.parseLong(spaceId)));
        LiteBusinessObject product = instanceSummaries.findById(Long.valueOf(spaceId)).orElseThrow();
        assertEquals(10030101L, product.type());
        assertEquals(584189545406816L, product.tracker());
        assertEquals(584190236948832L, instanceSummaries.findById(1L).orElseThrow().tracker());
        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications WHERE classification_id NOT IN (10030101, 10080101, 10090101)", Integer.class);
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND is_default", Integer.class, spaceId));
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'",
                Integer.class, spaceId));
        List<TrackerView> trackers = Arrays.stream(rest.getForObject("/api/worksets/1/trackers", TrackerView[].class))
                .filter(tracker -> spaceId.equals(tracker.productSpaceId())).toList();
        assertEquals(classifications, trackers.size());
        assertEquals(classifications, jdbc.queryForObject("""
                SELECT count(*)
                FROM air_inst_tracker_rev r JOIN air_ws_tracker c ON c.id = r.id
                JOIN classifications metadata ON metadata.classification_id = r.tracker_class
                WHERE r.work_space = ? AND r.workingset_id = 1 AND c.workingset_id = 1
                  AND r.id = r.instance_id AND r.id = r.common_id
                  AND r.revision = 1 AND r.revision_id IS NULL AND r.version_type = 10001
                  AND r.base_class = metadata.base_class_id AND r.class = metadata.class_id
                  AND r.type = r.tracker_class
                  AND r.tracker = 584578917040480 AND r.default_layout = 584532998805760
                  AND r.owner = ? AND r.number = c.number AND r.tracker_class = c.sub_class_id
                  AND r.share = c.shared AND r.is_default = 1 AND c.is_default = 1
                  AND r.created_time = c.created_time AND r.created_by = c.created_by
                """, Integer.class, Long.valueOf(spaceId), RequestThreadHolder.getLongUserId()));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*)
                FROM air_inst_tracker_rev r JOIN air_ws_tracker c ON c.id = r.id
                WHERE r.work_space = ? AND r.workingset_id = 1 AND c.workingset_id = 1
                  AND r.tracker_class = 10070101 AND c.sub_class_id = 10070101
                  AND r.base_class = 1007 AND r.class = 100701 AND r.type = 10070101
                  AND r.tracker = 584578917040480 AND r.share = c.shared AND r.is_default = 1 AND c.is_default = 1
                """, Integer.class, Long.valueOf(spaceId)));
        for (TrackerView tracker : trackers) {
            ResponseEntity<String> items = rest.getForEntity(
                    "/api/requirements?trackerId=" + tracker.trackerId() + "&workingSetId=1", String.class);
            assertEquals(HttpStatus.OK, items.getStatusCode(), items::getBody);
            assertEquals("[]", items.getBody());
        }

        BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        WorkingSetController.SpaceBindingView projectedSpace = bindings.spaces().stream()
                .filter(binding -> spaceId.equals(binding.productSpaceId())).findFirst().orElseThrow();
        assertEquals("RELATION", projectedSpace.origin());
        assertEquals(trackers.stream().map(TrackerView::trackerId).sorted().toList(),
                bindings.trackers().stream().filter(binding -> spaceId.equals(binding.productSpaceId()))
                        .map(binding -> binding.trackerId()).sorted().toList());

        String trackerId = trackers.stream().filter(tracker -> "14010101".equals(tracker.classificationId()))
                .findFirst().orElseThrow().trackerId();
        ResponseEntity<RequirementView> item = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId, "workingSetId", "1",
                "title", "验证默认 Tracker", "description", "验证绑定范围", "revision", 1), RequirementView.class);
        assertEquals(HttpStatus.CREATED, item.getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            RequirementView[] items = rest.getForObject(
                    "/api/requirements?trackerId=" + trackerId + "&workingSetId=1", RequirementView[].class);
            assertTrue(Arrays.stream(items).anyMatch(indexed -> item.getBody().localId().equals(indexed.localId())));
        });
    }

    @Test
    void keepsSpaceMountedWithOnlyItsLocalFolderEvenWhenHidden() {
        String space = createSpace("1");
        FixtureIds.insertWorkingSet(jdbc, "1", child);
        long folder = jdbc.queryForObject("""
                SELECT tracker_id FROM trackers
                WHERE working_set_id = 1 AND product_space_id = ? AND classification_id = 10070101 AND is_default
                """, Long.class, Long.valueOf(space));
        RestClient client = RestClient.builder().baseUrl(rest.getRootUri())
                .requestFactory(new JdkClientHttpRequestFactory()).build();
        long childFolder = Long.parseLong(fixtureId("folder-" + child));
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, 10070101, 'UNSHARED')
                """, childFolder, folder, child, space);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, 10070101, 'UNSHARED', 'UNSHARED_DERIVATION')
                """, child, childFolder, space);
        String path = "/api/worksets/" + child;
        String visibility = path + "/product-spaces/" + space + "/trackers/" + childFolder + "/visibility";
        HttpStatusCode wrongSpace = client.patch().uri(path + "/product-spaces/" + fixtureId("other-" + space)
                        + "/trackers/" + childFolder + "/visibility")
                .body(Map.of("shown", false)).exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, wrongSpace);
        HttpStatusCode oldRoute = client.patch().uri(path + "/trackers/" + childFolder + "/visibility")
                .body(Map.of("shown", false)).exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, oldRoute);
        assertEquals(1, rest.getForObject(path + "/trackers", TrackerView[].class).length);
        assertEquals(HttpStatus.NO_CONTENT, client.patch().uri(visibility).body(Map.of("shown", false))
                .retrieve().toBodilessEntity().getStatusCode());
        assertEquals(0, rest.getForObject(path + "/trackers", TrackerView[].class).length);
        BindingsView hidden = rest.getForObject(path + "/bindings", BindingsView.class);
        assertEquals(1, hidden.spaces().size());
        assertEquals("10070101", hidden.trackers().getFirst().classificationId());
        assertFalse(hidden.trackers().getFirst().shown());
        assertEquals(HttpStatus.NO_CONTENT, client.patch().uri(visibility).body(Map.of("shown", true))
                .retrieve().toBodilessEntity().getStatusCode());
        assertEquals(1, rest.getForObject(path + "/trackers", TrackerView[].class).length);
        HttpStatusCode deletion = client.delete().uri("/api/worksets/1/product-spaces/" + space)
                .exchange((request, response) -> response.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, deletion);
        assertEquals(1, rest.getForObject(path + "/bindings", BindingsView.class).spaces().size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM air_inst_tracker_rev WHERE id = ?",
                Integer.class, childFolder));
    }

    @Test
    void renamingTrackerUpdatesBothRowsAndDoesNotChangeIdentity() {
        String spaceId = createSpace("1");
        TrackerView tracker = rest.postForEntity("/api/worksets/1/trackers",
                Map.of("productSpaceId", spaceId, "classificationId", "14010101",
                        "sharingMode", "UNSHARED", "name", "变更前"), TrackerView.class).getBody();
        long id = Long.parseLong(tracker.trackerId());
        RestClient client = RestClient.builder().baseUrl(rest.getRootUri())
                .requestFactory(new JdkClientHttpRequestFactory()).build();

        TrackerView renamed = client.patch().uri("/api/trackers/{id}", id)
                .body(Map.of("name", "  变更后  ")).retrieve().body(TrackerView.class);

        assertEquals(tracker.trackerId(), renamed.trackerId());
        assertEquals("变更后", renamed.name());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_tracker_rev r JOIN air_ws_tracker c ON c.id = r.id
                WHERE r.id = ? AND r.name = ? AND c.name = r.name
                  AND r.common_id = r.id AND r.revision = 1 AND r.workingset_id = 1
                  AND r.lock_version = 1 AND c.lock_version = r.lock_version
                  AND r.updated_by = c.updated_by AND r.updated_time = c.updated_time
                """, Integer.class, id, "变更后"));
        LiteBusinessObject common = instanceSummaries.findById(id).orElseThrow();
        assertEquals("变更后", common.name());
        assertEquals(584578917040480L, common.tracker());
        assertEquals(1L, jdbc.queryForObject("SELECT lock_version FROM air_inst_common WHERE id = ?", Long.class, id));
        assertEquals(2, instanceSummaries.findByIds(List.of(id, 1L, id, Long.MAX_VALUE)).size());
        assertEquals(HttpStatus.NO_CONTENT, rest.exchange("/api/trackers/" + id, HttpMethod.DELETE,
                null, Void.class).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> instanceSummaries.findById(id).isEmpty());
    }

    @Test
    void commonIdentityCollisionRollsBackBothTrackerRows() {
        long space = Long.parseLong(createSpace("1"));
        Long id = Long.valueOf(fixtureId("COMMON-COLLISION-" + suffix));
        jdbc.update("INSERT INTO air_inst_common (id, base_class, class, type) VALUES (?, 1003, 100301, 10030101)",
                id);
        try {
            Tracker tracker = new Tracker(id, "projection collision", null, 1L,
                    space, classifications.findClassification("14010101").orElseThrow(),
                    Classification.SharingMode.UNSHARED, null, false, 419436436619264L);
            assertThrows(DataIntegrityViolationException.class,
                    () -> transactions.executeWithoutResult(status -> trackerRepository.save(tracker)));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_inst_tracker_rev WHERE id = ?",
                    Integer.class, id));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_ws_tracker WHERE id = ?",
                    Integer.class, id));
            assertEquals(10030101L, instanceSummaries.findById(id).orElseThrow().type());
        } finally {
            jdbc.update("DELETE FROM air_inst_common WHERE id = ?", id);
        }
    }

    @Test
    void rejectedCompatibilityRowRollsBackRootInsert() {
        long space = Long.parseLong(createSpace("1"));
        Long id = Long.valueOf(fixtureId("TRACKER-ROLLBACK-" + suffix));
        Tracker tracker = new Tracker(id, "x".repeat(501), null, 1L,
                space, classifications.findClassification("14010101").orElseThrow(),
                Classification.SharingMode.UNSHARED, null, false, 419436436619264L);

        assertThrows(DataIntegrityViolationException.class,
                () -> transactions.executeWithoutResult(status -> trackerRepository.save(tracker)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_inst_tracker_rev WHERE id = ?",
                Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_ws_tracker WHERE id = ?",
                Integer.class, id));
    }

    @Test
    void createsAndDeletesAnAdditionalTrackerInTheDefaultIncludedClassification() {
        String spaceId = createSpace("1");
        ResponseEntity<TrackerView> response = rest.postForEntity("/api/worksets/1/trackers",
                Map.of("productSpaceId", spaceId, "name", "第二个文件夹分类 Tracker",
                        "classificationId", "10070101", "sharingMode", "UNSHARED"), TrackerView.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        TrackerView tracker = response.getBody();
        assertFalse(tracker.isDefault());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
            assertTrue(bindings.trackers().stream().anyMatch(binding -> tracker.trackerId().equals(binding.trackerId())));
        });
        rest.delete("/api/trackers/" + tracker.trackerId());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM air_inst_tracker_rev WHERE id = ? AND delete_flag = 1", Integer.class, Long.valueOf(tracker.trackerId()))));
    }

    @Test
    void createsAndBindsNamedTrackersUsingClassificationSharingRules() {
        FixtureIds.insertClassification(jdbc, sharedClassification, "SHARED");
        String spaceId = createSpace("1");
        ClassificationView[] classifications = rest.getForObject("/api/worksets/metadata/classifications", ClassificationView[].class);
        assertTrue(Arrays.stream(classifications).anyMatch(classification ->
                sharedClassification.equals(classification.classificationId())
                        && classification.sharingMode() == Classification.SharingMode.SHARED));

        String url = "/api/worksets/1/trackers";
        for (Classification.SharingMode mode : Classification.SharingMode.values()) {
            Map<String, String> request = Map.of("productSpaceId", spaceId, "name", "项目-" + mode, "classificationId",
                    "14010101", "sharingMode", mode.name());
            ResponseEntity<TrackerView> response = rest.postForEntity(url, request, TrackerView.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            TrackerView tracker = response.getBody();
            assertEquals(mode.name(), tracker.sharingMode());
            assertEquals("项目-" + mode, tracker.name());
            assertFalse(tracker.isDefault());
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
                BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
                assertTrue(bindings.trackers().stream().anyMatch(binding -> tracker.trackerId().equals(binding.trackerId())));
            });
            assertEquals(HttpStatus.OK, rest.getForEntity(
                            "/api/requirements?trackerId=" + tracker.trackerId() + "&workingSetId=1", String.class)
                    .getStatusCode());
            assertEquals(HttpStatus.CONFLICT, rest.postForEntity(url, request, Map.class).getStatusCode());

            if (mode == Classification.SharingMode.SHARED) {
                FixtureIds.insertWorkingSet(jdbc, "1", child);
                FixtureIds.extendWithRootFolder(rest, jdbc, child, spaceId);
                FixtureIds.extendScope(rest, child,
                        WorkingSetCreation.Status.Succeeded, tracker.trackerId());
                assertEquals(1L, jdbc.queryForObject("""
                        SELECT r.workingset_id FROM air_inst_tracker_rev r
                        JOIN air_ws_tracker c ON c.id = r.id AND c.workingset_id = r.workingset_id
                        WHERE r.id = ?
                        """, Long.class, Long.valueOf(tracker.trackerId())));
                assertEquals(HttpStatus.CONFLICT, rest.postForEntity("/api/worksets/" + child + "/trackers",
                        request, Map.class).getStatusCode());
            }
        }
        Map<String, String> allowed = Map.of("productSpaceId", spaceId, "name", "仅共享", "classificationId",
                sharedClassification, "sharingMode", "SHARED");
        ResponseEntity<TrackerView> allowedResponse = rest.postForEntity(url, allowed, TrackerView.class);
        assertEquals(HttpStatus.CREATED, allowedResponse.getStatusCode());
        String allowedTrackerId = allowedResponse.getBody().trackerId();
        await().atMost(Duration.ofSeconds(8)).until(() -> rest.getForObject("/api/worksets/1/bindings", BindingsView.class)
                .trackers().stream().anyMatch(binding -> allowedTrackerId.equals(binding.trackerId())));
        Map<String, String> rejected = Map.of("productSpaceId", spaceId, "name", "禁止非共享", "classificationId",
                sharedClassification, "sharingMode", "UNSHARED");
        ResponseEntity<Map> refusal = rest.postForEntity(url, rejected, Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refusal.getStatusCode());
        assertEquals("SharingModeMismatch", refusal.getBody().get("reason"));
    }

    @Test
    void defaultTrackerIsUniquePerWorkingSetSpaceAndClassification() {
        String spaceId = fixtureId("PS-DEF-" + suffix);
        testSpaces.add(spaceId);
        jdbc.update("INSERT INTO air_inst_product_space (id, name, lock_version, delete_flag, created_by, created_time, updated_by, updated_time) VALUES (?, ?, 0, 0, 'test', 0, 'test', 0)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications WHERE classification_id = 14010101", String.class);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, fixtureId("T-DEF1-" + suffix), "默认1-" + suffix, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, fixtureId("T-DEF2-" + suffix), "默认2-" + suffix, spaceId, classification));
    }

    @Test
    void trackerNameIsUniqueWithinSpaceAndClassification() {
        String spaceId = fixtureId("PS-NAME-" + suffix);
        testSpaces.add(spaceId);
        jdbc.update("INSERT INTO air_inst_product_space (id, name, lock_version, delete_flag, created_by, created_time, updated_by, updated_time) VALUES (?, ?, 0, 0, 'test', 0, 'test', 0)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications WHERE classification_id = 14010101", String.class);
        String name = "同名-" + suffix;
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, fixtureId("T-N1-" + suffix), name, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, fixtureId("T-N2-" + suffix), name, spaceId, classification));
    }

    @Test
    void trackerBindingsNoLongerEnforceOneTrackerPerSpaceAndClassification() {
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'tracker_bindings'
                  AND indexdef LIKE '%(working_set_id, product_space_id, classification_id)%'
                """, Integer.class));
    }

    @Test
    void productSpaceCreationFromNonDefaultEntryStillTargetsDefaultWorkingSet() {
        FixtureIds.insertWorkingSet(jdbc, "1", child);
        String spaceId = createSpace(child);

        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications WHERE classification_id NOT IN (10030101, 10080101, 10090101)", Integer.class);
        assertEquals(classifications, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'
                """, Integer.class, spaceId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = ?
                """, Integer.class, spaceId, child));

        BindingsView defaultBindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        assertTrue(defaultBindings.spaces().stream().anyMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "default working set should own the projected space binding");
        BindingsView childBindings = rest.getForObject("/api/worksets/" + child + "/bindings", BindingsView.class);
        assertTrue(childBindings.spaces().stream().noneMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "product space must not be projected into the child working set");
    }

    @Test
    void originalTrackerCannotBeCreatedInChildWorkingSet() {
        ProductSpace space = new ProductSpace(fixtureId("space-domain-" + suffix), "domain");
        Classification classification = new Classification(258831141156800L, Classification.SharingMode.UNSHARED,
                "测试分类", 11L, 111L);
        assertThrows(DomainException.class, () -> new Tracker(
                Long.valueOf(fixtureId("tracker-domain-" + suffix)), "子工作集原生 Tracker", null,
                Long.valueOf(child), space.numericId(), classification, classification.sharingMode(), null, false));
    }

    @Test
    void trackerNameUniquenessDoesNotCrossProductSpaces() {
        String firstSpace = fixtureId("PS-NAME1-" + suffix);
        String secondSpace = fixtureId("PS-NAME2-" + suffix);
        String classification = jdbc.queryForObject(
                "SELECT classification_id FROM classifications WHERE classification_id = 14010101", String.class);
        String name = "同名跨空间-" + suffix;
        for (String spaceId : Arrays.asList(firstSpace, secondSpace)) {
            testSpaces.add(spaceId);
            jdbc.update("INSERT INTO air_inst_product_space (id, name, lock_version, delete_flag, created_by, created_time, updated_by, updated_time) VALUES (?, ?, 0, 0, 'test', 0, 'test', 0)",
                    spaceId, "空间-" + suffix);
            jdbc.update("""
                    INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                    VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                    """, fixtureId("T-NAME-" + spaceId), name, spaceId, classification);
            jdbc.update("""
                    INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                    VALUES (1, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                    """, fixtureId("T-NAME-" + spaceId), spaceId, classification);
        }
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE name = ? AND working_set_id = '1'",
                Integer.class, name));
        BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        List<TrackerView> trackers = Arrays.asList(rest.getForObject("/api/worksets/1/trackers", TrackerView[].class));
        for (String spaceId : List.of(firstSpace, secondSpace)) {
            String trackerId = fixtureId("T-NAME-" + spaceId);
            assertEquals(Long.valueOf(spaceId), trackerRepository.findById(Long.valueOf(trackerId)).orElseThrow().productSpaceId());
            assertTrue(bindings.spaces().stream().anyMatch(binding -> spaceId.equals(binding.productSpaceId())));
            assertTrue(bindings.trackers().stream().anyMatch(binding -> trackerId.equals(binding.trackerId())));
            assertTrue(trackers.stream().anyMatch(tracker -> trackerId.equals(tracker.trackerId())));
        }
    }

    private String createSpace(String workingSet) {
        ResponseEntity<ProductSpaceView> response = rest.postForEntity("/api/worksets/" + workingSet + "/product-spaces",
                Map.of("name", "新空间-" + suffix),
                ProductSpaceView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().productSpaceId();
        testSpaces.add(id);
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications WHERE classification_id NOT IN (10030101, 10080101, 10090101)", Integer.class);
            assertEquals(classifications, jdbc.queryForObject(
                    "SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'",
                    Integer.class, id));
            BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
            assertTrue(bindings.spaces().stream().anyMatch(binding -> id.equals(binding.productSpaceId())));
        });
        return id;
    }
}
