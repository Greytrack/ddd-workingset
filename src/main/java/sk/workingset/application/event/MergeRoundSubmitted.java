package sk.workingset.application.event;

/** 请求异步执行一个已登记的 MergeRound。 */
public record MergeRoundSubmitted(String roundId) {}
