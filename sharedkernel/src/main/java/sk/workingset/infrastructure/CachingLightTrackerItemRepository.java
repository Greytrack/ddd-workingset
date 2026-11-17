package sk.workingset.infrastructure;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * LightTrackerItem 查询缓存装饰器（Redis，TTL + 提交后更新）。
 *
 * <p>SK 应用层仍只依赖 {@link LightTrackerItemRepository}；本类位于 infrastructure，隔离 Redis/Jackson。
 * 缓存只保存聚合元数据；{@code findBySource} 不做缓存，避免来源列表变化后的失效复杂度。
 * Redis 不可用时降级到下游仓储，不影响业务正确性。
 */
public class CachingLightTrackerItemRepository implements LightTrackerItemRepository {

    private static final Logger log = LoggerFactory.getLogger(CachingLightTrackerItemRepository.class);
    private static final String KEY_PREFIX = "workingset:lti:";
    private static final Duration TTL = Duration.ofSeconds(30);

    private final LightTrackerItemRepository delegate;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CachingLightTrackerItemRepository(LightTrackerItemRepository delegate,
                                             StringRedisTemplate redis,
                                             ObjectMapper objectMapper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.redis = Objects.requireNonNull(redis, "redis");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void save(LightTrackerItem item) {
        delegate.save(item);
        var snapshot = CachedLightTrackerItem.from(item);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // A nested merge operation can roll back while its outer transaction still commits.
                    try {
                        delegate.findById(item.localId()).map(CachedLightTrackerItem::from)
                                .ifPresentOrElse(CachingLightTrackerItemRepository.this::writeCache,
                                        () -> invalidate(item.localId()));
                    } catch (RuntimeException failure) {
                        invalidate(item.localId());
                    }
                }
            });
        } else {
            writeCache(snapshot);
        }
    }

    @Override
    public Optional<LightTrackerItem> findById(LocalId localId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return delegate.findById(localId);
        }
        LightTrackerItem cached = readCache(localId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<LightTrackerItem> found = delegate.findById(localId);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isSynchronizationActive()) {
            found.map(CachedLightTrackerItem::from).ifPresent(this::writeCache);
        }
        return found;
    }

    @Override
    public List<LightTrackerItem> findInTracker(TrackerId trackerId) {
        return delegate.findInTracker(trackerId);
    }

    @Override
    public List<LightTrackerItem> pageInTracker(TrackerId trackerId, String afterLocalId, int limit) {
        return delegate.pageInTracker(trackerId, afterLocalId, limit);
    }

    @Override
    public boolean insertIfFamilyAbsent(LightTrackerItem item) {
        return delegate.insertIfFamilyAbsent(item);
    }

    @Override
    public boolean hasFamily(WorkingSetId workingSetId, CommonId commonId) {
        return delegate.hasFamily(workingSetId, commonId);
    }

    @Override
    public boolean hasSourceBackedDependents(LocalId id) {
        return delegate.hasSourceBackedDependents(id);
    }

    @Override
    public void remove(LocalId id) {
        delegate.remove(id);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate(id);
                }
            });
        } else {
            invalidate(id);
        }
    }

    private void invalidate(LocalId id) {
        try {
            redis.delete(key(id));
        } catch (Exception failure) {
            log.warn("Redis cache eviction failed for localId={}", id.value(), failure);
        }
    }

    @Override
    public boolean existsInTracker(TrackerId trackerId) {
        return delegate.existsInTracker(trackerId);
    }

    @Override
    public List<LightTrackerItem> findBySource(LocalId sourceLocalId, Revision sourceRevision) {
        return delegate.findBySource(sourceLocalId, sourceRevision);
    }

    private LightTrackerItem readCache(LocalId localId) {
        try {
            String json = redis.opsForValue().get(key(localId));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, CachedLightTrackerItem.class).toDomain();
        } catch (Exception failure) {
            log.warn("Redis cache read failed for localId={}, falling back to repository", localId.value(), failure);
            return null;
        }
    }

    private void writeCache(CachedLightTrackerItem item) {
        try {
            String json = objectMapper.writeValueAsString(item);
            redis.opsForValue().set(KEY_PREFIX + item.localId(), json, TTL);
        } catch (Exception failure) {
            log.warn("Redis cache write failed for localId={}, continuing without cache", item.localId(), failure);
        }
    }

    private static String key(LocalId localId) {
        return KEY_PREFIX + localId.value();
    }
}
