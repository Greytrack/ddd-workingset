package sk.workingset.domain.event;

/**
 * 请求异步执行一个已登记的 MergeRound。
 */
public record MergeRoundSubmittedEvent(String roundId) {
}
