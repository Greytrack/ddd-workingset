package sk.workingset.application.port;

public interface TargetTransactionRunner {
    void inNewTransaction(Runnable work);

    /** 在独立事务中执行并返回结果。 */
    default <T> T inNewTransaction(java.util.function.Supplier<T> work) {
        var holder = new Object[1];
        inNewTransaction((Runnable) () -> holder[0] = work.get());
        @SuppressWarnings("unchecked")
        T result = (T) holder[0];
        return result;
    }
}
