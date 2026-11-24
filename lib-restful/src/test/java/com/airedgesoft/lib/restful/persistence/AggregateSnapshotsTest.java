package com.airedgesoft.lib.restful.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事务级加载快照（持久化侧）单测：基线随聚合实例隔离、存库后推进、身份级兜底。
 */
class AggregateSnapshotsTest {

    @Test
    void baseline_isIsolatedPerAggregateInstance() {
        // 同一资源 id 的两个实例各自持有自己的加载版本（CAS 基线随实例快照，不按 id 单槽）
        inTransaction(() -> {
            Object first = new Object();
            Object second = new Object();
            AggregateSnapshots.recordLoaded("demoTasks/101", first, 0L);
            AggregateSnapshots.recordLoaded("demoTasks/101", second, 1L);

            assertThat(AggregateSnapshots.baselineOf(first)).isEqualTo(0L);
            assertThat(AggregateSnapshots.baselineOf(second)).isEqualTo(1L);
        });
    }

    @Test
    void recordSaved_advancesInstanceBaseline() {
        // 同事务内连续保存：第一次推进该实例的基线，第二次以新版本做 CAS
        inTransaction(() -> {
            Object instance = new Object();
            AggregateSnapshots.recordLoaded("demoTasks/101", instance, 0L);
            assertThat(AggregateSnapshots.baselineOf(instance)).isEqualTo(0L);

            AggregateSnapshots.recordSaved("demoTasks/101", instance, 1L);
            assertThat(AggregateSnapshots.baselineOf(instance)).isEqualTo(1L);
            assertThat(AggregateSnapshots.currentVersionOf("demoTasks/101")).isEqualTo(1L);
        });
    }

    @Test
    void firstSnapshot_keepsFirstLoadPerIdentity() {
        inTransaction(() -> {
            AggregateSnapshots.recordLoaded("demoTasks/101", new Object(), 0L);
            AggregateSnapshots.recordLoaded("demoTasks/101", new Object(), 5L);
            assertThat(AggregateSnapshots.firstSnapshotOf("demoTasks/101")).isEqualTo(0L);
        });
    }

    @Test
    void outsideTransaction_noBaseline() {
        assertThat(AggregateSnapshots.baselineOf(new Object())).isNull();
        assertThat(AggregateSnapshots.firstSnapshotOf("demoTasks/101")).isNull();
    }

    /** 轻量事务管理器：只开启事务同步（供事务级状态使用），不绑定资源。 */
    private static final class SyncOnlyTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private void inTransaction(Runnable action) {
        PlatformTransactionManager manager = new SyncOnlyTransactionManager();
        new TransactionTemplate(manager).executeWithoutResult(status -> action.run());
    }
}
