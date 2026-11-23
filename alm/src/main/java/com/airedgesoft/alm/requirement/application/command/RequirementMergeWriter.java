package com.airedgesoft.alm.requirement.application.command;


import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.RequestThreadHolder;
import com.airedgesoft.sharedkernel.workingset.domain.event.BusinessObjectDeleteEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryApprovedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryResolvedEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.MergeEntryResolvedEvent.Outcome;
import com.airedgesoft.sharedkernel.workingset.error.MergeResCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * ALM 对 Merge 条目的应用边界：整体校验、提交并返回结果事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RequirementMergeWriter {
    private final RequirementMutationStore repository;
    private final RequirementCommandService requirements;

    private static MergeEntryResolvedEvent rejected(MergeEntryApprovedEvent command, Outcome outcome,
                                                    String failure) {
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), outcome, null,
                command.sourceRevision(), command.targetRevision(), false, command.inherited(), failure);
    }

    private static Requirement create(MergeEntryApprovedEvent command) {
        if (command.operation() == MergeEntryApprovedEvent.Operation.RESTORE) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "deleted target is absent");
        }
        return Requirement.create(command.localId(), command.commonId(), command.trackerId(),
                text(command.acceptedFields(), "title", text(command.baseFields(), "title", null)),
                text(command.acceptedFields(), "description", text(command.baseFields(), "description", null)),
                command.targetRevision(), command.version(),
                RequestThreadHolder.getUserId());
    }

    private static String text(Map<String, Object> values, String field, String fallback) {
        Object value = values.getOrDefault(field, fallback);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Requirement field is unreadable: " + field);
        }
        return text;
    }

    /** 应用已通过幂等检查的合并条目并返回业务结果。 */
    public MergeEntryResolvedEvent apply(MergeEntryApprovedEvent command) {
        try {
            MergeEntryResolvedEvent applied = applyCommand(command);
            return applied;
        } catch (LockVersionConflictException failure) {
            return rejected(command, Outcome.CONFLICT, failure.getMessage());
        } catch (DomainException failure) {
            return rejected(command, failure.getResCode() == MergeResCode.CONFLICT ? Outcome.CONFLICT : Outcome.REJECTED,
                    failure.getMessage());
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return rejected(command, Outcome.REJECTED, failure.getMessage());
        }
    }

    private MergeEntryResolvedEvent applyCommand(MergeEntryApprovedEvent command) {
        if (command.operation() == MergeEntryApprovedEvent.Operation.CREATE
                && repository.lockFamilyInWorkingSet(command.trackerId(), command.commonId()).isPresent()) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "target working set family is already occupied");
        }
        Optional<Requirement> existing = repository.lockById(command.localId());
        if (command.expectedLockVersion() == null && existing.isPresent()
                || command.expectedLockVersion() != null && existing.isEmpty()) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "target business state changed");
        }
        if (existing.isEmpty()) {
            Requirement created = create(command);
            repository.save(created);
            if (command.operation() == MergeEntryApprovedEvent.Operation.DELETE) {
                RequirementSnapshot snapshot = RequirementSnapshot.of(created);
                int beforeRevision = created.revision();
                long beforeLockVersion = created.lockVersion();
                BusinessObjectDeleteEvent deletedEvent = created.delete(created.lockVersion());
                requirements.saveDeleted(snapshot, created, beforeRevision, beforeLockVersion, deletedEvent);
            }
            return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                    created.localId(), command.sourceRevision(), created.revision(), true,
                    command.inherited(), null);
        }
        Requirement current = existing.orElseThrow();
        if (!current.trackerId().equals(command.trackerId()) || !current.commonId().equals(command.commonId())) {
            throw DomainException.withMessage(MergeResCode.CONFLICT, "target identity changed");
        }
        Long expected = command.expectedLockVersion();
        Requirement changed = current;
        BusinessObjectDeleteEvent deletedEvent = null;
        switch (command.operation()) {
            case UPDATE -> changed = current.changeContent(
                    text(command.acceptedFields(), "title", current.title()),
                    text(command.acceptedFields(), "description", current.description()), expected);
            case DELETE -> {
                RequirementSnapshot snapshot = RequirementSnapshot.of(current);
                int beforeRevision = current.revision();
                long beforeLockVersion = current.lockVersion();
                deletedEvent = current.delete(expected);
                requirements.saveDeleted(snapshot, current, beforeRevision, beforeLockVersion, deletedEvent);
                changed = current;
            }
            case RESTORE -> changed = current.restoreContent(text(command.baseFields(), "title", null),
                    text(command.baseFields(), "description", null), expected);
            case CREATE -> throw DomainException.withMessage(MergeResCode.CONFLICT, "target already exists");
        }
        if (deletedEvent == null) {
            requirements.saveChange(current, changed);
        }
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                changed.localId(), command.sourceRevision(), changed.revision(), false,
                command.inherited(), null);
    }
}
