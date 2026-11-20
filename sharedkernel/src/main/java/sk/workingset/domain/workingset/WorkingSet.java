package sk.workingset.domain.workingset;

import sk.workingset.error.BindingResCode;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Entity;
import sk.productspace.domain.ProductSpace;
import sk.common.error.DomainException;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.error.WorkingsetResultCode;

import java.util.*;

/**
 * WorkingSet 聚合根：默认根不可删除、单父谱系（{@code parentWorkingSetId}）、
 * TrackerBinding 与 SpaceBinding 的归属与解除规则。
 */
@AggregateRoot
public final class WorkingSet {

    public static final WorkingSetId DEFAULT_ID = WorkingSetId.of("1");
    public static final String DEFAULT_COLOR = "#FFFF";
    private static final java.util.regex.Pattern HEX_COLOR =
            java.util.regex.Pattern.compile("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private final WorkingSetId id;
    private final WorkingSetId parentWorkingSetId;
    private String name;
    private String color;
    private final boolean newAggregate;
    private final Map<String, SpaceBinding> spaces = new LinkedHashMap<>();
    private final Map<TrackerId, TrackerBinding> trackers = new LinkedHashMap<>();
    private final Map<TrackerId, TrackerBinding> originalTrackers;

    public WorkingSet(WorkingSetId id, WorkingSetId parentWorkingSetId,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, null, spaces, trackers);
    }

    public WorkingSet(WorkingSetId id, WorkingSetId parentWorkingSetId, String name,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, name, DEFAULT_COLOR, spaces, trackers);
    }

    public WorkingSet(WorkingSetId id, WorkingSetId parentWorkingSetId, String name, String color,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, name, color, spaces, trackers, false);
    }

    private WorkingSet(WorkingSetId id, WorkingSetId parentWorkingSetId, String name, String color,
                       Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers, boolean newAggregate) {
        this.id = Objects.requireNonNull(id, "id");
        if (id.equals(parentWorkingSetId)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, id.value());
        }
        if (DEFAULT_ID.equals(id) != (parentWorkingSetId == null)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "only the default Working Set has no parent");
        }
        this.parentWorkingSetId = parentWorkingSetId;
        this.name = name;
        this.color = color;
        this.newAggregate = newAggregate;
        spaces.forEach(binding -> this.spaces.put(binding.productSpaceId(), binding));
        trackers.forEach(binding -> this.trackers.put(binding.trackerId(), binding));
        originalTrackers = Map.copyOf(this.trackers);
    }

    public static WorkingSet child(WorkingSetId id, WorkingSetId parentId) {
        return child(id, parentId, null);
    }

    public static WorkingSet child(WorkingSetId id, WorkingSetId parentId, String name) {
        return child(id, parentId, name, null);
    }

    public static WorkingSet child(WorkingSetId id, WorkingSetId parentId, String name, String color) {
        return new WorkingSet(id, Objects.requireNonNull(parentId, "parentId"), name,
                normalizeColor(color), List.of(), List.of(), true);
    }

    public static String normalizeColor(String color) {
        if (color == null) {
            return DEFAULT_COLOR;
        }
        String normalized = color.strip();
        if (!HEX_COLOR.matcher(normalized).matches()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "color must be a hexadecimal color");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    public static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must not be blank");
        }
        if (trimmed.length() > 255) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain at most 255 characters");
        }
        return trimmed;
    }

    /** 编辑工作集显示名称；名称是展示属性，不改变谱系、范围或绑定。 */
    public void rename(String newName) {
        if (newName == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name is required");
        }
        this.name = normalizeName(newName);
    }

    /** 编辑工作集主题色；颜色只影响界面呈现，不参与领域比较或合并。 */
    public void recolor(String newColor) {
        if (newColor == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "color is required");
        }
        this.color = normalizeColor(newColor);
    }

    public static void requireDeletable(WorkingSetId id) {
        if (DEFAULT_ID.equals(id)) {
            throw DomainException.withMessage(BindingResCode.DEFAULT_WORKING_SET_UNDELETABLE, id.value());
        }
    }

    public WorkingSetId id() {
        return id;
    }

    public boolean isNew() {
        return newAggregate;
    }

    public WorkingSetId parentWorkingSetId() {
        return parentWorkingSetId;
    }

    public String name() {
        return name;
    }

    public String color() {
        return color;
    }

    public List<SpaceBinding> spaces() {
        return List.copyOf(spaces.values());
    }

    public List<TrackerBinding> trackers() {
        return List.copyOf(trackers.values());
    }

    public List<TrackerBinding> addedTrackerBindings() {
        return trackers.values().stream()
                .filter(binding -> !originalTrackers.containsKey(binding.trackerId()))
                .toList();
    }

    public List<TrackerId> removedTrackerBindings() {
        return originalTrackers.keySet().stream()
                .filter(trackerId -> !trackers.containsKey(trackerId))
                .sorted(Comparator.comparing(TrackerId::numericValue))
                .toList();
    }

    public List<TrackerBinding> changedTrackerVisibility() {
        return trackers.values().stream()
                .filter(binding -> {
                    TrackerBinding original = originalTrackers.get(binding.trackerId());
                    return original != null && original.shown() != binding.shown();
                })
                .toList();
    }

    public void setTrackerShown(TrackerId trackerId, boolean shown) {
        TrackerBinding binding = trackerBinding(trackerId);
        trackers.put(trackerId, new TrackerBinding(id, trackerId, binding.numericProductSpaceId(),
                binding.numericClassificationId(), binding.sharingMode(), binding.origin(), shown));
    }

    public SpaceBinding bindSpace(ProductSpace space) {
        return bindSpace(space, SpaceOrigin.EXPLICIT);
    }

    public SpaceBinding bindSpace(ProductSpace space, SpaceOrigin origin) {
        WorkingSet.SpaceBinding binding = new SpaceBinding(id, space.id(), space.name(), origin, SpaceState.ACTIVE);
        if (spaces.putIfAbsent(space.id(), binding) != null) {
            throw DomainException.withMessage(BindingResCode.ALREADY_BOUND, space.id());
        }
        return binding;
    }

    public TrackerBinding trackerBinding(TrackerId trackerId) {
        WorkingSet.TrackerBinding binding = trackers.get(trackerId);
        if (binding == null) {
            throw DomainException.withMessage(BindingResCode.TRACKER_BINDING_NOT_FOUND, trackerId.value());
        }
        return binding;
    }

    public void unbindTracker(TrackerId trackerId) {
        trackerBinding(trackerId);
        trackers.remove(trackerId);
    }

    public void removeSpace(String spaceId) {
        if (trackers.values().stream().anyMatch(binding -> binding.productSpaceId().equals(spaceId))) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_HAS_TRACKERS, spaceId);
        }
        if (spaces.remove(spaceId) == null) {
            throw DomainException.withMessage(BindingResCode.SPACE_BINDING_NOT_FOUND, spaceId);
        }
    }

    public WorkingSetId requireParentId() {
        if (parentWorkingSetId == null) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_NOT_WRITABLE, "default Working Set cannot bind upstream Trackers");
        }
        return parentWorkingSetId;
    }

    public boolean hasTrackerBinding(TrackerId trackerId) {
        return trackers.containsKey(trackerId);
    }

    public boolean hasSpaceBinding(String spaceId) {
        return spaces.containsKey(spaceId);
    }

    public void requireSpaceBinding(String spaceId) {
        if (!hasSpaceBinding(spaceId)) {
            throw DomainException.withMessage(BindingResCode.MISSING_SPACE_BINDING, spaceId);
        }
    }

    public void requireDirectParent(Tracker source, boolean boundInParent) {
        WorkingSetId parentId = requireParentId();
        boolean ownedByParent = parentId.equals(source.workingSetId());
        // 默认根的原生 Tracker 直接来自平台投影。子工作集的有效范围由绑定定义。
        boolean inParentRange = DEFAULT_ID.equals(parentId) ? ownedByParent : boundInParent;
        if (!inParentRange || (source.sharingMode() == SharingMode.UNSHARED && !ownedByParent)) {
            throw DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, source.trackerId().value());
        }
    }

    public TrackerBinding bindTracker(Tracker tracker, Classification classification, SharingMode requestedMode) {
        requireSpaceBinding(tracker.productSpaceId());
        if (!tracker.classificationId().equals(classification.id())
                || tracker.sharingMode() != requestedMode || !classification.allows(requestedMode)) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, classification.id());
        }
        if (trackers.containsKey(tracker.trackerId())) {
            throw DomainException.withMessage(BindingResCode.TRACKER_ALREADY_BOUND, tracker.trackerId().value());
        }
        WorkingSet.TrackerOrigin origin = requestedMode == SharingMode.SHARED ? TrackerOrigin.SHARED_REUSE : TrackerOrigin.UNSHARED_DERIVATION;
        WorkingSet.TrackerBinding binding = new TrackerBinding(id, tracker.trackerId(), tracker.productSpaceId(), classification.id(),
                requestedMode, origin);
        trackers.put(binding.trackerId(), binding);
        return binding;
    }

    public enum SpaceOrigin {INITIAL_PROJECTION, EXPLICIT, RELATION}

    public enum SpaceState {ACTIVE}

    public enum TrackerOrigin {SHARED_REUSE, UNSHARED_DERIVATION}

    @Entity
    public record SpaceBinding(WorkingSetId workingSetId, Long numericProductSpaceId, String name,
                               SpaceOrigin origin, SpaceState state) {
        public SpaceBinding(WorkingSetId workingSetId, String productSpaceId, String name,
                            SpaceOrigin origin, SpaceState state) {
            this(workingSetId, Long.valueOf(productSpaceId), name, origin, state);
        }

        public SpaceBinding(WorkingSetId workingSetId, String productSpaceId,
                            SpaceOrigin origin, SpaceState state) {
            this(workingSetId, productSpaceId, null, origin, state);
        }

        public String productSpaceId() {
            return numericProductSpaceId.toString();
        }
    }

    @Entity
    public record TrackerBinding(WorkingSetId workingSetId, TrackerId trackerId, Long numericProductSpaceId,
                                 Long numericClassificationId, SharingMode sharingMode, TrackerOrigin origin, boolean shown) {
        public TrackerBinding(WorkingSetId workingSetId, TrackerId trackerId, Long numericProductSpaceId,
                              Long numericClassificationId, SharingMode sharingMode, TrackerOrigin origin) {
            this(workingSetId, trackerId, numericProductSpaceId, numericClassificationId, sharingMode, origin, true);
        }

        public TrackerBinding(WorkingSetId workingSetId, TrackerId trackerId, String productSpaceId,
                              String classificationId, SharingMode sharingMode, TrackerOrigin origin) {
            this(workingSetId, trackerId, Long.valueOf(productSpaceId), Long.valueOf(classificationId),
                    sharingMode, origin);
        }

        public String productSpaceId() {
            return numericProductSpaceId.toString();
        }

        public String classificationId() {
            return numericClassificationId.toString();
        }
    }
}
