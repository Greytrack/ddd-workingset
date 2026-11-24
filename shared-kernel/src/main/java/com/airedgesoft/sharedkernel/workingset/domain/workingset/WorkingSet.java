package com.airedgesoft.sharedkernel.workingset.domain.workingset;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.workingset.domain.event.WorkingSetDeleted;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Entity;

import java.util.*;
import java.util.regex.Pattern;

/**
 * WorkingSet 聚合根：默认根不可删除、单父谱系（{@code parentWorkingSetId}）、
 * TrackerBinding 与 SpaceBinding 的归属与解除规则。
 */
@AggregateRoot
public final class WorkingSet {

    public static final Long DEFAULT_ID = Long.valueOf("1");
    public static final String DEFAULT_COLOR = "#FFFF";
    private static final Pattern HEX_COLOR =
            Pattern.compile("#(?:[0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private final Long id;
    private final Long parentWorkingSetId;
    private final boolean newAggregate;
    private final Map<String, SpaceBinding> spaces = new LinkedHashMap<>();
    private final Map<Long, TrackerBinding> trackers = new LinkedHashMap<>();
    private final Map<Long, TrackerBinding> originalTrackers;
    private final Long owner;
    private String name;
    private String color;
    private String actor;
    private boolean deleted;

    public WorkingSet(Long id, Long parentWorkingSetId,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, null, spaces, trackers);
    }

    public WorkingSet(Long id, Long parentWorkingSetId, String name,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, name, DEFAULT_COLOR, spaces, trackers);
    }

    public WorkingSet(Long id, Long parentWorkingSetId, String name, String color,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers) {
        this(id, parentWorkingSetId, name, color, spaces, trackers, null, null);
    }

    public WorkingSet(Long id, Long parentWorkingSetId, String name, String color,
                      Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers,
                      Long owner, String actor) {
        this(id, parentWorkingSetId, name, color, spaces, trackers, false, owner, actor);
    }

    private WorkingSet(Long id, Long parentWorkingSetId, String name, String color,
                       Collection<SpaceBinding> spaces, Collection<TrackerBinding> trackers,
                       boolean newAggregate, Long owner, String actor) {
        this.id = Objects.requireNonNull(id, "id");
        if (id <= 0 || (parentWorkingSetId != null && parentWorkingSetId <= 0)) {
            throw new IllegalArgumentException("Working set IDs must be positive");
        }
        if (id.equals(parentWorkingSetId)) {
            throw new DomainException(BindingResCode.WORKING_SET_CYCLE, Long.toString(id));
        }
        if (DEFAULT_ID.equals(id) != (parentWorkingSetId == null)) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "only the default Working Set has no parent");
        }
        this.parentWorkingSetId = parentWorkingSetId;
        this.name = name;
        this.color = color;
        this.newAggregate = newAggregate;
        this.owner = owner;
        this.actor = actor;
        spaces.forEach(binding -> this.spaces.put(binding.productSpaceId(), binding));
        trackers.forEach(binding -> this.trackers.put(binding.trackerId(), binding));
        originalTrackers = Map.copyOf(this.trackers);
    }

    public static WorkingSet child(Long id, Long parentId) {
        return child(id, parentId, null);
    }

    public static WorkingSet child(Long id, Long parentId, String name) {
        return child(id, parentId, name, null);
    }

    public static WorkingSet child(Long id, Long parentId, String name, String color) {
        return child(id, parentId, name, color, null, null);
    }

    public static WorkingSet child(Long id, Long parentId, String name, String color,
                                   Long owner, String actor) {
        return new WorkingSet(id, Objects.requireNonNull(parentId, "parentId"), name,
                normalizeColor(color), List.of(), List.of(), true, owner, actor);
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

    public static void requireDeletable(Long id) {
        if (DEFAULT_ID.equals(id)) {
            throw new DomainException(BindingResCode.DEFAULT_WORKING_SET_UNDELETABLE, Long.toString(id));
        }
    }

    /**
     * 标记聚合已删除；调用方持久化该状态后发布删除事实。
     */
    public WorkingSetDeleted delete(String actor) {
        requireDeletable(id);
        if (deleted) {
            throw new DomainException(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id));
        }
        audit(actor);
        deleted = true;
        return new WorkingSetDeleted(id);
    }

    public boolean deleted() {
        return deleted;
    }

    /**
     * 编辑工作集显示名称；名称是展示属性，不改变谱系、范围或绑定。
     */
    public void rename(String newName) {
        if (newName == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name is required");
        }
        this.name = normalizeName(newName);
    }

    /**
     * 编辑工作集主题色；颜色只影响界面呈现，不参与领域比较或合并。
     */
    public void recolor(String newColor) {
        if (newColor == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "color is required");
        }
        this.color = normalizeColor(newColor);
    }

    public Long id() {
        return id;
    }

    public boolean isNew() {
        return newAggregate;
    }

    /**
     * 记录本次业务操作者；写侧仓储直接使用该审计事实，不再读取请求线程上下文。
     */
    public void audit(String actor) {
        this.actor = Objects.requireNonNull(actor, "actor");
    }

    public Long owner() {
        return owner;
    }

    public String actor() {
        return actor;
    }

    public Long parentWorkingSetId() {
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

    public List<Long> removedTrackerBindings() {
        return originalTrackers.keySet().stream()
                .filter(trackerId -> !trackers.containsKey(trackerId))
                .sorted(Comparator.comparing(Long::longValue))
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

    public void setTrackerShown(long spaceId, Long trackerId, boolean shown) {
        TrackerBinding binding = trackerBinding(trackerId);
        if (binding.numericProductSpaceId() != spaceId) {
            throw new DomainException(BindingResCode.TRACKER_BINDING_NOT_FOUND, Long.toString(trackerId));
        }
        trackers.put(trackerId, new TrackerBinding(id, trackerId, binding.numericProductSpaceId(),
                binding.numericClassificationId(), binding.shared(), binding.origin(), shown));
    }

    public SpaceBinding bindSpace(ProductSpace space) {
        return bindSpace(space, SpaceOrigin.EXPLICIT);
    }

    public SpaceBinding bindSpace(ProductSpace space, SpaceOrigin origin) {
        SpaceBinding binding = new SpaceBinding(id, space.id(), space.name(), origin, SpaceState.ACTIVE);
        if (spaces.putIfAbsent(space.id(), binding) != null) {
            throw new DomainException(BindingResCode.ALREADY_BOUND, space.id());
        }
        return binding;
    }

    public TrackerBinding trackerBinding(Long trackerId) {
        TrackerBinding binding = trackers.get(trackerId);
        if (binding == null) {
            throw new DomainException(BindingResCode.TRACKER_BINDING_NOT_FOUND, Long.toString(trackerId));
        }
        return binding;
    }

    public void unbindTracker(Long trackerId) {
        trackerBinding(trackerId);
        trackers.remove(trackerId);
    }

    public void removeSpace(String spaceId) {
        if (trackers.values().stream().anyMatch(binding -> binding.productSpaceId().equals(spaceId))) {
            throw new DomainException(BindingResCode.SPACE_STILL_HAS_TRACKERS, spaceId);
        }
        if (spaces.remove(spaceId) == null) {
            throw new DomainException(BindingResCode.SPACE_BINDING_NOT_FOUND, spaceId);
        }
    }

    public Long requireParentId() {
        if (parentWorkingSetId == null) {
            throw new DomainException(BindingResCode.WORKING_SET_NOT_WRITABLE, "default Working Set cannot bind upstream Trackers");
        }
        return parentWorkingSetId;
    }

    public boolean hasTrackerBinding(Long trackerId) {
        return trackers.containsKey(trackerId);
    }

    public boolean hasSpaceBinding(String spaceId) {
        return spaces.containsKey(spaceId);
    }

    public void requireSpaceBinding(String spaceId) {
        if (!hasSpaceBinding(spaceId)) {
            throw new DomainException(BindingResCode.MISSING_SPACE_BINDING, spaceId);
        }
    }

    public void requireDirectParent(Tracker source, boolean boundInParent) {
        Long parentId = requireParentId();
        boolean ownedByParent = parentId.equals(source.workingSetId());
        // 默认根的原生 Tracker 直接来自平台投影。子工作集的有效范围由绑定定义。
        boolean inParentRange = DEFAULT_ID.equals(parentId) ? ownedByParent : boundInParent;
        if (!inParentRange || (!source.shared() && !ownedByParent)) {
            throw new DomainException(BindingResCode.SOURCE_TRACKER_NOT_FOUND, Long.toString(source.trackerId()));
        }
    }

    public TrackerBinding bindTracker(Tracker tracker, ClassSnapshot classification, boolean requestedShared) {
        requireSpaceBinding(tracker.productSpaceId().toString());
        if (!tracker.classificationId().equals(String.valueOf(classification.getId()))
                || tracker.shared() != requestedShared || !classification.allows(requestedShared)) {
            throw new DomainException(BindingResCode.SHARING_MODE_MISMATCH, String.valueOf(classification.getId()));
        }
        if (trackers.containsKey(tracker.trackerId())) {
            throw new DomainException(BindingResCode.TRACKER_ALREADY_BOUND, Long.toString(tracker.trackerId()));
        }
        TrackerOrigin origin = requestedShared ? TrackerOrigin.SHARED_REUSE : TrackerOrigin.UNSHARED_DERIVATION;
        TrackerBinding binding = new TrackerBinding(id, tracker.trackerId(), tracker.productSpaceId(), classification.getId(),
                requestedShared, origin);
        trackers.put(binding.trackerId(), binding);
        return binding;
    }

    public enum SpaceOrigin {INITIAL_PROJECTION, EXPLICIT, RELATION}

    public enum SpaceState {ACTIVE}

    public enum TrackerOrigin {SHARED_REUSE, UNSHARED_DERIVATION}

    @Entity
    public record SpaceBinding(Long workingSetId, Long numericProductSpaceId, String name,
                               SpaceOrigin origin, SpaceState state) {
        public SpaceBinding(Long workingSetId, String productSpaceId, String name,
                            SpaceOrigin origin, SpaceState state) {
            this(workingSetId, Long.valueOf(productSpaceId), name, origin, state);
        }

        public SpaceBinding(Long workingSetId, String productSpaceId,
                            SpaceOrigin origin, SpaceState state) {
            this(workingSetId, productSpaceId, null, origin, state);
        }

        public String productSpaceId() {
            return numericProductSpaceId.toString();
        }
    }

    @Entity
    public record TrackerBinding(Long workingSetId, Long trackerId, Long numericProductSpaceId,
                                 Long numericClassificationId, boolean shared, TrackerOrigin origin,
                                 boolean shown) {
        public TrackerBinding(Long workingSetId, Long trackerId, Long numericProductSpaceId,
                              Long numericClassificationId, boolean shared, TrackerOrigin origin) {
            this(workingSetId, trackerId, numericProductSpaceId, numericClassificationId, shared, origin, true);
        }

        public TrackerBinding(Long workingSetId, Long trackerId, String productSpaceId,
                              String classificationId, boolean shared, TrackerOrigin origin) {
            this(workingSetId, trackerId, Long.valueOf(productSpaceId), Long.valueOf(classificationId),
                    shared, origin);
        }

        public String productSpaceId() {
            return numericProductSpaceId.toString();
        }

        public String classificationId() {
            return numericClassificationId.toString();
        }
    }
}
