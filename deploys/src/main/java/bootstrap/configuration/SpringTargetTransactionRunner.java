package bootstrap.configuration;

import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.port.TargetTransactionRunner;

/** {@link TargetTransactionRunner} 的 Spring 实现：每个调用开启 REQUIRES_NEW 事务。 */
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
