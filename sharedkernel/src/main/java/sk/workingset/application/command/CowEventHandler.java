package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.query.provider.CowFailureLogQueryProvider;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.event.BusinessObjectUpdateEvent;
import sk.workingset.domain.event.TrackerItemMaterializationRequestedEvent;
import sk.workingset.domain.repository.CowMaterializationStore;
import sk.workingset.domain.repository.LightTrackerItemRepository;

import java.time.Instant;

/**
 * COW 请求处理器：来源变化后，为未物化目标发布逐目标实例化请求。
 *
 * <p>业务保存由 ALM 监听请求事件后在自己的事务中完成；SK 只记录收据并等待完成事件，
 * 不再同步调用业务 BC 的写入能力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class CowEventHandler {
    private final LightTrackerItemRepository items;
    private final CowFailureLogQueryProvider failureLog;
    private final TransactionTemplate transactionTemplate;
    private final CowMaterializationStore store;
    private final ApplicationEventPublisher events;

    private static TrackerItemMaterializationRequestedEvent request(BusinessObjectUpdateEvent event,
                                                                    LightTrackerItem target) {
        return new TrackerItemMaterializationRequestedEvent(event.eventId(), target.localId(),
                target.trackerId(), target.commonId(), event.sourceLocalId(), event.beforeRevision(),
                event.afterRevision(), event.beforeLockVersion(), event.afterLockVersion(),
                target.revision(), event.snapshotRef());
    }

    public void onTrackerItemChanged(BusinessObjectUpdateEvent event) {
        for (LightTrackerItem target : items.findSourceBackedTargets(event.sourceLocalId(), event.beforeRevision())) {
            if (target.materialized()) {
                continue;
            }
            boolean alreadyHandled = store.findBySource(event.sourceLocalId()).stream()
                    .anyMatch(entry -> entry.targetLocalId().equals(target.localId()));
            if (alreadyHandled) {
                continue;
            }
            int attempts = 1;
            try {
                CowMaterializationStore.Entry receipt = transactionTemplate.execute(status ->
                        store.markPending(event.eventId(), target.localId(),
                                event.sourceLocalId(), event.beforeRevision()));
                attempts = receipt.attempts();
                if (receipt.status() == CowMaterializationStore.Status.SWITCHED) {
                    continue;
                }
                events.publishEvent(request(event, target));
            } catch (RuntimeException error) {
                CowFailureLogQueryProvider.Failure failure = new CowFailureLogQueryProvider.Failure(event.eventId(), target.localId(),
                        event.sourceLocalId(), event.beforeRevision(), event.afterRevision(),
                        event.beforeLockVersion(), event.afterLockVersion(), attempts, "REQUEST",
                        error.getClass().getName(), error.getMessage(), Instant.now());
                try {
                    transactionTemplate.executeWithoutResult(status ->
                            store.markFailed(event.eventId(), target.localId(), error.getMessage()));
                } catch (RuntimeException ignored) {
                    // 失败终态以原异常为准。
                }
                try {
                    failureLog.record(failure);
                } catch (RuntimeException ignored) {
                    // 失败终态以原异常为准。
                }
            }
        }
    }
}
