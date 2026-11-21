package alm.requirement.domain;

import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;

import java.util.Locale;
import java.util.Objects;

/**
 * ALM Requirement。业务版次、发布版本与乐观锁分别维护。
 */
public final class Requirement implements TrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final String number;
    private final String title;
    private final String description;
    private final Revision revision;
    private final Version version;
    private final LockVersion lockVersion;
    private final boolean materialized;
    private final boolean deleteFlag;
    private final String createdBy;

    private Requirement(LocalId localId, CommonId commonId, TrackerId trackerId, String title, String description,
                        Revision revision, Version version, LockVersion lockVersion, boolean materialized,
                        boolean deleteFlag, String createdBy) {
        this.localId = Objects.requireNonNull(localId, "localId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.number = "REQ-" + Long.toHexString(commonId.numericValue()).toUpperCase(Locale.ROOT);
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.version = Objects.requireNonNull(version, "version");
        this.lockVersion = Objects.requireNonNull(lockVersion, "lockVersion");
        this.materialized = materialized;
        this.deleteFlag = deleteFlag;
        this.createdBy = createdBy;
    }

    public static Requirement create(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                     String description, Revision revision) {
        return create(id, commonId, trackerId, title, description, revision, Version.none());
    }

    public static Requirement create(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                     String description, Revision revision, Version version) {
        return create(id, commonId, trackerId, title, description, revision, version, null);
    }

    public static Requirement create(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                     String description, Revision revision, Version version, String createdBy) {
        validateTitle(title);
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                LockVersion.initial(), true, false, createdBy);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version, LockVersion lockVersion) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion, true, false);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version,
                                      LockVersion lockVersion, boolean deleteFlag) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion, true, deleteFlag);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version,
                                      LockVersion lockVersion, boolean materialized, boolean deleteFlag) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion,
                materialized, deleteFlag, null);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version,
                                      LockVersion lockVersion, boolean materialized, boolean deleteFlag,
                                      String createdBy) {
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                lockVersion, materialized, deleteFlag, createdBy);
    }

    /**
     * 构造尚未实例化的继承内容视图：仅用于读取来源内容，不持久化。
     */
    public static Requirement inherited(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                        String description, Revision revision, Version version) {
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                LockVersion.initial(), false, false, null);
    }

    private static LockVersion requireExpectedLockVersion(LifecycleCommand command) {
        if (command == null || command.expectedLockVersion() == null) {
            throw new DomainException(RequirementResultCode.LOCK_VERSION_REQUIRED);
        }
        return command.expectedLockVersion();
    }

    private static void validateTitle(String title) {
        if (Objects.requireNonNull(title, "title").isBlank()) {
            throw new DomainException(RequirementResultCode.TITLE_REQUIRED);
        }
    }

    @Override
    public LocalId localId() {
        return localId;
    }

    @Override
    public CommonId commonId() {
        return commonId;
    }

    @Override
    public Revision revision() {
        return revision;
    }

    @Override
    public boolean hasMaterialized() {
        return this.materialized;
    }

    public TrackerId trackerId() {
        return trackerId;
    }

    public String number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public Version version() {
        return version;
    }

    public LockVersion lockVersion() {
        return lockVersion;
    }

    public boolean deleted() {
        return deleteFlag;
    }

    public String createdBy() {
        return createdBy;
    }

    public Requirement changeTitle(String title, LockVersion expectedLockVersion) {
        return changeContent(title, description, expectedLockVersion);
    }

    public Requirement changeContent(String title, String description, LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), materialized, false, createdBy);
    }

    public Requirement upgradeRevision(LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description,
                Revision.of(Math.incrementExact(revision.value())), version, lockVersion.next(), materialized, false, createdBy);
    }

    public Requirement delete(LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), materialized, true, createdBy);
    }

    public Requirement undelete(LockVersion expectedLockVersion) {
        return restoreContent(title, description, expectedLockVersion);
    }

    public Requirement restoreContent(String title, String description, LockVersion expectedLockVersion) {
        verifyLockVersion(expectedLockVersion);
        if (!deleteFlag) {
            throw new DomainException(RequirementResultCode.ALREADY_ACTIVE);
        }
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), materialized, false, createdBy);
    }

    @Override
    public LifecycleResult delete(LifecycleCommand command) {
        try {
            return LifecycleResult.applied(delete(requireExpectedLockVersion(command)));
        } catch (LockVersionConflictException conflict) {
            return LifecycleResult.conflict(conflict.getMessage());
        } catch (DomainException alreadyDeleted) {
            if (alreadyDeleted.getResCode() != RequirementResultCode.DELETED) {
                throw alreadyDeleted;
            }
            return LifecycleResult.rejected(alreadyDeleted.getMessage());
        }
    }

    @Override
    public LifecycleResult undelete(LifecycleCommand command) {
        try {
            return LifecycleResult.applied(undelete(requireExpectedLockVersion(command)));
        } catch (LockVersionConflictException conflict) {
            return LifecycleResult.conflict(conflict.getMessage());
        } catch (DomainException invalid) {
            if (invalid.getResCode() != RequirementResultCode.ALREADY_ACTIVE && invalid.getResCode() != RequirementResultCode.LOCK_VERSION_REQUIRED && invalid.getResCode() != RequirementResultCode.TITLE_REQUIRED) {
                throw invalid;
            }
            return LifecycleResult.rejected(invalid.getMessage());
        }
    }

    @Override
    public LifecycleResult delete2(LifecycleCommand command) {
        return deleted() ? LifecycleResult.applied(this)
                : LifecycleResult.conflict("Requirement must be soft-deleted before permanent deletion");
    }

    private void requireActive() {
        if (deleteFlag) {
            throw new DomainException(RequirementResultCode.DELETED);
        }
    }

    private void verifyLockVersion(LockVersion expected) {
        if (!lockVersion.equals(Objects.requireNonNull(expected, "expectedLockVersion"))) {
            throw new LockVersionConflictException(expected, lockVersion);
        }
    }
}
