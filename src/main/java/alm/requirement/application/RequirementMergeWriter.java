package alm.requirement.application;

import java.util.List;
import java.util.Map;

import alm.requirement.domain.LockVersionConflictException;
import alm.requirement.domain.Requirement;
import alm.requirement.infrastructure.JdbcRequirementRepository;
import sk.workingset.application.port.RequirementMergeCommand;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Version;
import sk.workingset.domain.merge.MergeFailure;
import sk.workingset.domain.merge.MergeFailure.Reason;

/** ALM 写入边界。按既有 Requirement 行为校验和提交整个条目。 */
public final class RequirementMergeWriter {
    private final JdbcRequirementRepository repository;
    private final RequirementApplicationService requirements;

    public RequirementMergeWriter(JdbcRequirementRepository repository, RequirementApplicationService requirements) {
        this.repository = repository;
        this.requirements = requirements;
    }

    public void lockEvidence(List<LocalId> ids) {
        ids.forEach(repository::lockById);
    }

    public RequirementMergeCommand.Result apply(RequirementMergeCommand command) {
        try {
            return applyChecked(command);
        } catch (LockVersionConflictException failure) {
            throw new MergeFailure(Reason.Conflict, failure.getMessage());
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new MergeFailure(Reason.Rejected, failure.getMessage());
        }
    }

    private RequirementMergeCommand.Result applyChecked(RequirementMergeCommand command) {
        if (command.mergeBackCreate()) {
            var familyMember = repository.lockFamilyInWorkingSet(command.trackerId(), command.commonId());
            if (familyMember.isPresent()) {
                return restoreMergeBack(command, familyMember.orElseThrow());
            }
        }
        var existing = repository.lockById(command.localId());
        if (command.expectedLockVersion() == null && existing.isPresent()
                || command.expectedLockVersion() != null && existing.isEmpty()) {
            throw new MergeFailure(Reason.Conflict, "target business state changed");
        }
        if (existing.isEmpty()) {
            var created = create(command);
            repository.save(created);
            if (command.operation() == RequirementMergeCommand.Operation.DELETE) {
                repository.save(created.delete(created.lockVersion()));
            }
            return new RequirementMergeCommand.Result(created.localId(), created.revision(), true);
        }
        var current = existing.orElseThrow();
        if (!current.trackerId().equals(command.trackerId()) || !current.commonId().equals(command.commonId())) {
            throw new MergeFailure(Reason.Conflict, "target identity changed");
        }
        var expected = LockVersion.of(command.expectedLockVersion());
        var changed = switch (command.operation()) {
            case UPDATE -> current.changeContent(
                    text(command.acceptedFields(), "title", current.title()),
                    text(command.acceptedFields(), "description", current.description()), expected);
            case DELETE -> current.delete(expected);
            case RESTORE -> current.undelete(expected);
            case CREATE -> throw new MergeFailure(Reason.Conflict, "target already exists");
        };
        requirements.saveChange(current, changed);
        return new RequirementMergeCommand.Result(changed.localId(), changed.revision(), false);
    }

    private RequirementMergeCommand.Result restoreMergeBack(RequirementMergeCommand command, Requirement current) {
        if (!current.deleted() || !current.trackerId().equals(command.trackerId())) {
            throw new MergeFailure(Reason.Conflict, "target working set family is already occupied");
        }
        var changed = current.restoreContent(text(command.baseFields(), "title", null),
                text(command.baseFields(), "description", null), current.lockVersion());
        requirements.saveChange(current, changed);
        return new RequirementMergeCommand.Result(changed.localId(), changed.revision(), false);
    }

    private static Requirement create(RequirementMergeCommand command) {
        if (command.operation() == RequirementMergeCommand.Operation.RESTORE) {
            throw new MergeFailure(Reason.Conflict, "deleted target is absent");
        }
        return Requirement.create(command.localId(), command.commonId(), command.trackerId(),
                text(command.acceptedFields(), "title", text(command.baseFields(), "title", null)),
                text(command.acceptedFields(), "description", text(command.baseFields(), "description", null)),
                command.revision(), command.version() == null ? Version.none() : Version.of(command.version()));
    }

    private static String text(Map<String, Object> values, String field, String fallback) {
        Object value = values.getOrDefault(field, fallback);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Requirement field is unreadable: " + field);
        }
        return text;
    }
}
