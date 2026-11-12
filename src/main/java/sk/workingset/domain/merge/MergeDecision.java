package sk.workingset.domain.merge;

import java.util.Map;
import java.util.Set;

import sk.workingset.domain.merge.Comparison.Difference;
import sk.workingset.domain.merge.Comparison.Presence;
import sk.workingset.domain.merge.MergeFailure.Reason;

public record MergeDecision(Map<String, FieldAction> fields, ExistenceAction existence, boolean markAsMerged) {
    public enum FieldAction { ACCEPT, KEEP_TARGET, DEFER }
    public enum ExistenceAction { CREATE, RESTORE, DELETE, KEEP_TARGET, DEFER }

    public MergeDecision {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public boolean deferred() {
        return existence == ExistenceAction.DEFER || fields.containsValue(FieldAction.DEFER);
    }

    public void validate(Difference difference) {
        if (markAsMerged && deferred()) {
            throw invalid("DEFER cannot be marked as merged");
        }
        Presence source = difference.source().presence();
        Presence target = difference.target().presence();
        if (source == Presence.UNREADABLE || target == Presence.UNREADABLE) {
            throw invalid("comparison contains unreadable evidence");
        }
        if (source == Presence.PRESENT && target == Presence.PRESENT) {
            if (existence != null || fields.isEmpty() || !fields.keySet().equals(Set.copyOf(difference.fields()))) {
                throw invalid("supply one decision per differing field");
            }
        } else if (!fields.isEmpty() || existence == null || !allowed(existence, source, target)) {
            throw invalid("supply an applicable explicit existence decision");
        }
    }

    private static boolean allowed(ExistenceAction action, Presence source, Presence target) {
        return switch (action) {
            case CREATE -> source == Presence.PRESENT && target == Presence.ABSENT;
            case RESTORE -> source == Presence.PRESENT && target == Presence.DELETED;
            case DELETE -> source != Presence.PRESENT && target == Presence.PRESENT;
            case KEEP_TARGET, DEFER -> true;
        };
    }

    private static MergeFailure invalid(String message) {
        return new MergeFailure(Reason.InvalidDecision, message);
    }
}
