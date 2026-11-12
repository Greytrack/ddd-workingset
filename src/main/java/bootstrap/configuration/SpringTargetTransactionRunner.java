package bootstrap.configuration;

import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.port.TargetTransactionRunner;

public final class SpringTargetTransactionRunner implements TargetTransactionRunner {
    private final TransactionTemplate newTransactionTemplate;

    public SpringTargetTransactionRunner(TransactionTemplate newTransactionTemplate) {
        this.newTransactionTemplate = newTransactionTemplate;
    }

    @Override
    public void inNewTransaction(Runnable work) {
        newTransactionTemplate.executeWithoutResult(status -> work.run());
    }
}
