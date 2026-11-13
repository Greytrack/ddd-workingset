package alm.requirement.domain;

import java.util.Objects;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LifecycleCommand;
import sk.workingset.domain.LifecycleResult;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.TrackerItem;
import sk.workingset.domain.Version;

/** ALM Requirement。业务版次、发布版本与乐观锁分别维护。 */
public final class Requirement implements TrackerItem {

    private final LocalId localId;
    private final CommonId commonId;
    private final TrackerId trackerId;
    private final String title;
    private final String description;
    private final Revision revision;
    private final Version version;
    private final LockVersion lockVersion;
    private final boolean deleteFlag;

    private Requirement(LocalId localId, CommonId commonId, TrackerId trackerId, String title, String description,
                        Revision revision, Version version, LockVersion lockVersion, boolean deleteFlag) {
        this.localId = Objects.requireNonNull(localId, "localId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.revision = Objects.requireNonNull(revision, "revision");
        this.version = Objects.requireNonNull(version, "version");
        this.lockVersion = Objects.requireNonNull(lockVersion, "lockVersion");
        this.deleteFlag = deleteFlag;
    }

    public static Requirement create(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                     String description, Revision revision) {
        return create(id, commonId, trackerId, title, description, revision, Version.none());
    }

    public static Requirement create(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                     String description, Revision revision, Version version) {
        validateTitle(title);
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                LockVersion.initial(), false);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version, LockVersion lockVersion) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion, false);
    }

    public static Requirement restore(LocalId id, CommonId commonId, TrackerId trackerId, String title,
                                      String description, Revision revision, Version version,
                                      LockVersion lockVersion, boolean deleteFlag) {
        return new Requirement(id, commonId, trackerId, title, description, revision, version, lockVersion, deleteFlag);
    }

    @Override
    public LocalId localId() { return localId; }

    @Override
    public CommonId commonId() { return commonId; }

    @Override
    public Revision revision() { return revision; }

    public TrackerId trackerId() { return trackerId; }
    public String title() { return title; }
    public String description() { return description; }
    public Version version() { return version; }
    public LockVersion lockVersion() { return lockVersion; }
    public boolean deleted() { return deleteFlag; }

    public Requirement changeTitle(String title, LockVersion expectedLockVersion) {
        return changeContent(title, description, expectedLockVersion);
    }

    public Requirement changeContent(String title, String description, LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), false);
    }

    public Requirement upgradeRevision(LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description,
                Revision.of(Math.incrementExact(revision.value())), version, lockVersion.next(), false);
    }

    public Requirement delete(LockVersion expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), true);
    }

    public Requirement undelete(LockVersion expectedLockVersion) {
        return restoreContent(title, description, expectedLockVersion);
    }

    public Requirement restoreContent(String title, String description, LockVersion expectedLockVersion) {
        verifyLockVersion(expectedLockVersion);
        if (!deleteFlag) {
            throw new IllegalArgumentException("Requirement is already active");
        }
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                lockVersion.next(), false);
    }

    @Override
    public LifecycleResult delete(LifecycleCommand command) {
        try {
            return LifecycleResult.applied(delete(requireExpectedLockVersion(command)));
        } catch (IllegalStateException alreadyDeleted) {
            return LifecycleResult.rejected(alreadyDeleted.getMessage());
        } catch (LockVersionConflictException conflict) {
            return LifecycleResult.conflict(conflict.getMessage());
        }
    }

    @Override
    public LifecycleResult undelete(LifecycleCommand command) {
        try {
            return LifecycleResult.applied(undelete(requireExpectedLockVersion(command)));
        } catch (IllegalArgumentException invalid) {
            return LifecycleResult.rejected(invalid.getMessage());
        } catch (LockVersionConflictException conflict) {
            return LifecycleResult.conflict(conflict.getMessage());
        }
    }

    @Override
    public LifecycleResult delete2(LifecycleCommand command) {
        // 永久删除不要求先软删；对象已不存在时由应用服务/仓储返回 NOT_FOUND。
        return LifecycleResult.applied(this);
    }

    private static LockVersion requireExpectedLockVersion(LifecycleCommand command) {
        if (command == null || command.expectedLockVersion() == null) {
            throw new IllegalArgumentException("expectedLockVersion is required for delete/undelete");
        }
        return command.expectedLockVersion();
    }

    private void requireActive() {
        if (deleteFlag) {
            throw new IllegalStateException("Requirement is deleted");
        }
    }

    private static void validateTitle(String title) {
        if (Objects.requireNonNull(title, "title").isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
    }

    private void verifyLockVersion(LockVersion expected) {
        if (!lockVersion.equals(Objects.requireNonNull(expected, "expectedLockVersion"))) {
            throw new LockVersionConflictException(expected, lockVersion);
        }
    }
}
