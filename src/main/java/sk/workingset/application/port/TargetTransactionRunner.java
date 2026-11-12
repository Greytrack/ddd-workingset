package sk.workingset.application.port;

public interface TargetTransactionRunner {
    void inNewTransaction(Runnable work);
}
