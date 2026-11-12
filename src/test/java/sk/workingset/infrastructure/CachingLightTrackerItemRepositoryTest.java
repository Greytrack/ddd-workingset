package sk.workingset.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * Redis 查询缓存集成测试（需要本机 Redis 63179）。
 *
 * <p>下游替身始终查不到数据，用于证明命中来自缓存；写后更新用于证明切换状态不会被旧缓存覆盖。
 */
class CachingLightTrackerItemRepositoryTest {

    private static final CommonId C1 = CommonId.of("C1");
    private static final TrackerId T1 = TrackerId.of("T1");
    private static final WorkingSetId WS1 = WorkingSetId.of("WS1");
    private static final Revision R3 = Revision.of(3);
    private static final LocalId L0 = LocalId.of("L0");

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private CachingLightTrackerItemRepository repository;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("127.0.0.1", 63179);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        LightTrackerItemRepository emptyDelegate = new LightTrackerItemRepository() {
            @Override
            public void save(LightTrackerItem item) {
            }

            @Override
            public Optional<LightTrackerItem> findById(LocalId localId) {
                return Optional.empty();
            }

            @Override
            public List<LightTrackerItem> findInTracker(TrackerId trackerId) { return List.of(); }

            @Override
            public boolean hasSourceBackedDependents(LocalId id) { return false; }

            @Override
            public void remove(LocalId id) {}

            @Override
            public boolean existsInTracker(TrackerId trackerId) {
                return false;
            }

            @Override
            public List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision) {
                return List.of();
            }
        };
        repository = new CachingLightTrackerItemRepository(emptyDelegate, redis, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void rolled_back_write_never_publishes_local_backed_state_to_redis() {
        LocalId id = LocalId.of("cache-" + UUID.randomUUID());
        var item = LightTrackerItem.derive(id, C1, T1, WS1, R3, L0);
        repository.save(item);
        TransactionSynchronizationManager.initSynchronization();
        try {
            item.switchToLocalBacked(R3);
            repository.save(item);
            assertFalse(repository.findById(id).orElseThrow().materialized());
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(repository.findById(id).orElseThrow().materialized());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            redis.delete("workingset:lti:" + id.value());
        }
    }

    @Test
    void cache_serves_saved_item_and_write_through_updates_materialized_state() {
        LocalId localId = LocalId.of("cache-" + UUID.randomUUID());
        LightTrackerItem sourceBacked = LightTrackerItem.derive(localId, C1, T1, WS1, R3, L0);
        repository.save(sourceBacked);

        LightTrackerItem cached = repository.findById(localId).orElseThrow();
        assertFalse(cached.materialized(), "缓存应保存派生初始态");
        assertEquals(L0, cached.sourceLocalId());

        LightTrackerItem localBacked = LightTrackerItem.restore(
                localId, C1, T1, WS1, R3, localId, R3, true);
        repository.save(localBacked);

        LightTrackerItem updated = repository.findById(localId).orElseThrow();
        assertTrue(updated.materialized(), "写后更新应覆盖旧缓存");
        assertEquals(localId, updated.sourceLocalId());
        redis.delete("workingset:lti:" + localId.value());
    }
}
