package sk.workingset.domain.merge;

import sk.workingset.error.MergeResCode;

import sk.common.error.DomainException;

import org.jmolecules.ddd.annotation.ValueObject;
import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;

import java.util.Map;
import java.util.Set;

/**
 * 更新逐字段决策；新增和删除使用行级决策。
 */
@ValueObject
public record MergeDecision(Map<String, FieldAction> fields, ExistenceAction existence, boolean markAsMerged) {
    public MergeDecision {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    private static DomainException invalid(String message) {
        return DomainException.withMessage(MergeResCode.INVALID_DECISION, message);
    }

    public boolean confirmsOnSuccess(Difference difference) {
        return switch (difference.kind()) {
            case ADDED -> existence == ExistenceAction.ACCEPT;
            case DELETED -> false;
            case MODIFIED -> markAsMerged;
        };
    }

    public void validate(Difference difference) {
        Presence source = difference.source().presence();
        Presence target = difference.target().presence();
        if (source == Presence.UNREADABLE || target == Presence.UNREADABLE) {
            throw invalid("comparison contains unreadable evidence");
        }
        if (difference.kind() == Comparison.ChangeKind.MODIFIED) {
            if (source != Presence.PRESENT || target != Presence.PRESENT || existence != null
                    || fields.isEmpty() || !fields.keySet().equals(Set.copyOf(difference.fields()))) {
                throw invalid("supply one decision per differing field");
            }
        } else if (!fields.isEmpty() || existence == null || markAsMerged
                || difference.kind() != Difference.classify(difference.source(), difference.target())) {
            throw invalid("supply only a row decision for an addition or deletion");
        }
    }

    public enum FieldAction {ACCEPT, IGNORED}

    public enum ExistenceAction {ACCEPT, IGNORED}
}
