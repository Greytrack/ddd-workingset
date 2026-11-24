package com.airedgesoft.lib.restful.etag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EtagContext#capture()} / {@link EtagContext#restore} 跨线程传播契约（LRO Worker / 流式导出）： 期望版本与当前版本随快照传递，CAS
 * 基线不传播（目标线程重新加载时重建）。
 */
class EtagContextPropagationTest {

    @AfterEach
    void reset() {
        EtagContext.clear();
    }

    @Test
    void captureAndRestore_propagatesExpectedAndCurrent() {
        EtagContext.bindLockedVersion("demoTasks/101", 3L);
        EtagContext.recordLoaded("demoTasks/101", new Object(), 2L);
        EtagContext.Snapshot snapshot = EtagContext.capture();

        EtagContext.clear();
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isNull();

        EtagContext.restore(snapshot);
        assertThat(EtagContext.expectedStorageVersion("demoTasks/101")).isEqualTo(2L);
        assertThat(EtagContext.currentGeneration("demoTasks/101")).isEqualTo(3L);
    }

    @Test
    void emptyContext_capturesEmptySnapshot() {
        EtagContext.Snapshot snapshot = EtagContext.capture();
        assertThat(snapshot.expected()).isEmpty();
        assertThat(snapshot.current()).isEmpty();
        EtagContext.restore(snapshot);
        assertThat(EtagContext.currentGeneration("demoTasks/1")).isNull();
    }
}
