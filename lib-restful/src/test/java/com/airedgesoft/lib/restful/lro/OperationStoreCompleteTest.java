package com.airedgesoft.lib.restful.lro;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OperationStore#complete} 终态收口契约：CAS RUNNING → 终态并回读；已终态 / 不存在 → empty，不覆写既有终态。
 */
class OperationStoreCompleteTest {

    private final OperationStore store = new InMemoryOperationStore();

    @Test
    void complete_runningOperation_transitionsToTerminalAndReadsBack() {
        store.save(Operation.pending("1001", "import"));

        Optional<Operation> completed = store.complete("1001", op -> op.withResult("done"));

        assertThat(completed).isPresent();
        assertThat(completed.get().done()).isTrue();
        assertThat(store.find("1001").orElseThrow().response()).isEqualTo("done");
    }

    @Test
    void complete_alreadyTerminal_doesNotOverwrite() {
        store.save(Operation.pending("1002", "import").withResult("first"));

        Optional<Operation> again = store.complete("1002", op -> op.withResult("second"));

        assertThat(again).isEmpty();
        assertThat(store.find("1002").orElseThrow().response()).isEqualTo("first");
    }

    @Test
    void complete_missingOperation_returnsEmpty() {
        assertThat(store.complete("9999", op -> op.withResult("x"))).isEmpty();
    }
}
