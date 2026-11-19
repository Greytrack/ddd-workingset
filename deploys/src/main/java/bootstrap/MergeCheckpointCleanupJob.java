package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import sk.workingset.domain.merge.MergeCheckpointCleanupStore;

/**
 * 定时清理已被更新的合并基准超越的 Checkpoint。
 *
 * <p>只删除两侧都已有更高版次锚点可替代的行；仍有效的基准与已撤销记录保留，避免数据持续膨胀。
 */
@Component
public class MergeCheckpointCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(MergeCheckpointCleanupJob.class);

    private final MergeCheckpointCleanupStore store;

    public MergeCheckpointCleanupJob(MergeCheckpointCleanupStore store) {
        this.store = store;
    }

    @Scheduled(cron = "${workingset.merge.checkpoint-cleanup-cron:0 0 3 * * *}")
    public void cleanupSupersededCheckpoints() {
        int removed = store.deleteSupersededCheckpoints();
        if (removed > 0) {
            log.info("Removed {} superseded merge checkpoints", removed);
        }
    }
}
