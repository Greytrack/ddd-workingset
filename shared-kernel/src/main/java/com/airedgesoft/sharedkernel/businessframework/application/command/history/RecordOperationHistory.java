package com.airedgesoft.sharedkernel.businessframework.application.command.history;

import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory.Actor;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory.Origin;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory.Review;
import com.airedgesoft.sharedkernel.businessframework.domain.history.OperationHistory.Subject;

/** 独立投递给共享历史用例的操作快照。 */
public record RecordOperationHistory(Origin origin, Subject subject, Actor actor, Review review) {}
