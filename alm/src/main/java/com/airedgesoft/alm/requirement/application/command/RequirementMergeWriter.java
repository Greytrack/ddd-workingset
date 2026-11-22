package com.airedgesoft.alm.requirement.application.command;


import com.airedgesoft.alm.requirement.application.port.RequirementMutationStore;
import com.airedgesoft.alm.requirement.domain.LockVersionConflictException;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.RequestThreadHolder;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryApprovedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryResolvedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.MergeEntryResolvedEvent.Outcome;
import com.airedgesoft.sharekernel.workingset.error.MergeResCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * ALM 对 Merge 条目的应用边界：幂等、整体校验、提交并返回结果事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RequirementMergeWriter {
    private final RequirementMutationStore repository;
    private final RequirementCommandService requirements;
    private final RequirementMergeReceiptStore receipts;

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

    /**
     * 幂等应用合并条目：整体校验目标、提交并返回结果事件。
     */
    public MergeEntryResolvedEvent apply(MergeEntryApprovedEvent command) {
        Optional<MergeEntryResolvedEvent> existing = receipts.find(command.roundId(), command.entryId());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        MergeEntryResolvedEvent result = applyChecked(command);
        receipts.save(result);
        return result;
    }

    private MergeEntryResolvedEvent applyChecked(MergeEntryApprovedEvent command) {
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
                requirements.saveChange(created, created.delete(created.lockVersion()));
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
        Requirement changed = switch (command.operation()) {
            case UPDATE -> current.changeContent(
                    text(command.acceptedFields(), "title", current.title()),
                    text(command.acceptedFields(), "description", current.description()), expected);
            case DELETE -> current.delete(expected);
            case RESTORE -> current.restoreContent(text(command.baseFields(), "title", null),
                    text(command.baseFields(), "description", null), expected);
            case CREATE -> throw DomainException.withMessage(MergeResCode.CONFLICT, "target already exists");
        };
        requirements.saveChange(current, changed);
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                changed.localId(), command.sourceRevision(), changed.revision(), false,
                command.inherited(), null);
    }
}
