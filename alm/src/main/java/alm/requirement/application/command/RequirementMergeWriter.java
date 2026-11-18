package alm.requirement.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import sk.common.error.DomainException;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.Version;
import sk.workingset.domain.event.MergeEntryApprovedEvent;
import sk.workingset.domain.event.MergeEntryResolvedEvent;
import sk.workingset.domain.event.MergeEntryResolvedEvent.Outcome;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;

import alm.requirement.application.port.RequirementMutationStore;
import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;

/** ALM 对 Merge 条目的应用边界：幂等、整体校验、提交并返回结果事件。 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class RequirementMergeWriter {
    private final RequirementMutationStore repository;
    private final RequirementCommandService requirements;
    private final RequirementMergeReceiptStore receipts;


    public MergeEntryResolvedEvent apply(MergeEntryApprovedEvent command) {
        java.util.Optional<MergeEntryResolvedEvent> existing = receipts.find(command.roundId(), command.entryId());
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
        } catch (MergeFailure failure) {
            return rejected(command, failure.reason() == Reason.Conflict ? Outcome.CONFLICT : Outcome.REJECTED,
                    failure.getMessage());
        } catch (DomainException | IllegalArgumentException | IllegalStateException failure) {
            return rejected(command, Outcome.REJECTED, failure.getMessage());
        }
    }

    private MergeEntryResolvedEvent applyCommand(MergeEntryApprovedEvent command) {
        if (command.mergeBackCreate()) {
            java.util.Optional<Requirement> familyMember = repository.lockFamilyInWorkingSet(
                    command.trackerId(), command.commonId());
            if (familyMember.isPresent()) {
                return restoreMergeBack(command, familyMember.orElseThrow());
            }
        }
        java.util.Optional<Requirement> existing = repository.lockById(command.localId());
        if (command.expectedLockVersion() == null && existing.isPresent()
                || command.expectedLockVersion() != null && existing.isEmpty()) {
            throw new MergeFailure(Reason.Conflict, "target business state changed");
        }
        if (existing.isEmpty()) {
            Requirement created = create(command);
            repository.save(created);
            if (command.operation() == MergeEntryApprovedEvent.Operation.DELETE) {
                repository.save(created.delete(created.lockVersion()));
            }
            return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                    created.localId(), command.sourceRevision(), created.revision(), true,
                    command.inherited(), null);
        }
        Requirement current = existing.orElseThrow();
        if (!current.trackerId().equals(command.trackerId()) || !current.commonId().equals(command.commonId())) {
            throw new MergeFailure(Reason.Conflict, "target identity changed");
        }
        LockVersion expected = LockVersion.of(command.expectedLockVersion());
        Requirement changed = switch (command.operation()) {
            case UPDATE -> current.changeContent(
                    text(command.acceptedFields(), "title", current.title()),
                    text(command.acceptedFields(), "description", current.description()), expected);
            case DELETE -> current.delete(expected);
            case RESTORE -> current.undelete(expected);
            case CREATE -> throw new MergeFailure(Reason.Conflict, "target already exists");
        };
        requirements.saveChange(current, changed);
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                changed.localId(), command.sourceRevision(), changed.revision(), false,
                command.inherited(), null);
    }

    private MergeEntryResolvedEvent restoreMergeBack(MergeEntryApprovedEvent command, Requirement current) {
        if (!current.deleted() || !current.trackerId().equals(command.trackerId())) {
            throw new MergeFailure(Reason.Conflict, "target working set family is already occupied");
        }
        Requirement changed = current.restoreContent(text(command.baseFields(), "title", null),
                text(command.baseFields(), "description", null), current.lockVersion());
        requirements.saveChange(current, changed);
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), Outcome.APPLIED,
                changed.localId(), command.sourceRevision(), changed.revision(), false,
                command.inherited(), null);
    }

    private static MergeEntryResolvedEvent rejected(MergeEntryApprovedEvent command, Outcome outcome,
                                                    String failure) {
        return new MergeEntryResolvedEvent(command.roundId(), command.entryId(), outcome, null,
                command.sourceRevision(), command.targetRevision(), false, command.inherited(), failure);
    }

    private static Requirement create(MergeEntryApprovedEvent command) {
        if (command.operation() == MergeEntryApprovedEvent.Operation.RESTORE) {
            throw new MergeFailure(Reason.Conflict, "deleted target is absent");
        }
        return Requirement.create(command.localId(), command.commonId(), command.trackerId(),
                text(command.acceptedFields(), "title", text(command.baseFields(), "title", null)),
                text(command.acceptedFields(), "description", text(command.baseFields(), "description", null)),
                command.targetRevision(), command.version() == null ? Version.none() : Version.of(command.version()));
    }

    private static String text(Map<String, Object> values, String field, String fallback) {
        Object value = values.getOrDefault(field, fallback);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Requirement field is unreadable: " + field);
        }
        return text;
    }
}
