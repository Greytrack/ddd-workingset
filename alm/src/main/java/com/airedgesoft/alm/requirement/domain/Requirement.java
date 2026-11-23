package com.airedgesoft.alm.requirement.domain;


import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.domain.LifecycleCommand;
import com.airedgesoft.sharekernel.workingset.domain.LifecycleResult;
import com.airedgesoft.sharekernel.workingset.domain.TrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectDeleteEvent;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;

import java.util.Locale;
import java.util.Objects;

/**
 * ALM Requirement。业务版次、发布版本与乐观锁分别维护。
 */
public final class Requirement implements TrackerItem {

    private final Long localId;
    private final Long commonId;
    private final Long trackerId;
    private final String number;
    private final String title;
    private final String description;
    private final Integer revision;
    private final String version;
    private Long lockVersion;
    private final boolean materialized;
    private boolean deleteFlag;
    private final String createdBy;

    private Requirement(Long localId, Long commonId, Long trackerId, String title, String description,
                        Integer revision, String version, Long lockVersion, boolean materialized,
                        boolean deleteFlag, String createdBy) {
        this.localId = Objects.requireNonNull(localId, "localId");
        this.commonId = Objects.requireNonNull(commonId, "commonId");
        this.trackerId = Objects.requireNonNull(trackerId, "trackerId");
        if (localId <= 0 || commonId <= 0 || trackerId <= 0) {
            throw new IllegalArgumentException("Requirement IDs must be positive");
        }
        this.number = "REQ-" + Long.toHexString(commonId).toUpperCase(Locale.ROOT);
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.revision = Objects.requireNonNull(revision, "revision");
        if (version != null && version.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "version must not be blank when present");
        }
        this.version = version;
        this.lockVersion = Objects.requireNonNull(lockVersion, "lockVersion");
        if (revision < 0 || lockVersion < 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "revision and lockVersion must not be negative");
        }
        this.materialized = materialized;
        this.deleteFlag = deleteFlag;
        this.createdBy = createdBy;
    }

    public static Requirement create(Long id, Long commonId, Long trackerId, String title,
                                     String description, Integer revision) {
        return create(id, commonId, trackerId, title, description, revision, null);
    }

    public static Requirement create(Long id, Long commonId, Long trackerId, String title,
                                     String description, Integer revision, String version) {
        return create(id, commonId, trackerId, title, description, revision, version, null);
    }

    public static Requirement create(Long id, Long commonId, Long trackerId, String title,
                                     String description, Integer revision, String version, String createdBy) {
        validateTitle(title);
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                Long.valueOf(0L), true, false, createdBy);
    }

    public static Requirement restore(Long id, Long commonId, Long trackerId, String title,
                                      String description, Integer revision, String version, Long lockVersion) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion, true, false);
    }

    public static Requirement restore(Long id, Long commonId, Long trackerId, String title,
                                      String description, Integer revision, String version,
                                      Long lockVersion, boolean deleteFlag) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion, true, deleteFlag);
    }

    public static Requirement restore(Long id, Long commonId, Long trackerId, String title,
                                      String description, Integer revision, String version,
                                      Long lockVersion, boolean materialized, boolean deleteFlag) {
        return restore(id, commonId, trackerId, title, description, revision, version, lockVersion,
                materialized, deleteFlag, null);
    }

    public static Requirement restore(Long id, Long commonId, Long trackerId, String title,
                                      String description, Integer revision, String version,
                                      Long lockVersion, boolean materialized, boolean deleteFlag,
                                      String createdBy) {
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                lockVersion, materialized, deleteFlag, createdBy);
    }

    /**
     * 构造尚未实例化的继承内容视图：仅用于读取来源内容，不持久化。
     */
    public static Requirement inherited(Long id, Long commonId, Long trackerId, String title,
                                        String description, Integer revision, String version) {
        return new Requirement(id, commonId, trackerId, title, description, revision, version,
                Long.valueOf(0L), false, false, null);
    }

    private static Long requireExpectedLockVersion(LifecycleCommand command) {
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
    public Long localId() {
        return localId;
    }

    @Override
    public Long commonId() {
        return commonId;
    }

    @Override
    public Integer revision() {
        return revision;
    }

    @Override
    public boolean hasMaterialized() {
        return this.materialized;
    }

    public Long trackerId() {
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

    public String version() {
        return version;
    }

    public Long lockVersion() {
        return lockVersion;
    }

    public boolean deleted() {
        return deleteFlag;
    }

    public String createdBy() {
        return createdBy;
    }

    public Requirement changeTitle(String title, Long expectedLockVersion) {
        return changeContent(title, description, expectedLockVersion);
    }

    public Requirement changeContent(String title, String description, Long expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                Math.incrementExact(lockVersion), materialized, false, createdBy);
    }

    public Requirement upgradeRevision(Long expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        return new Requirement(localId, commonId, trackerId, title, description,
                Math.incrementExact(revision), version, Math.incrementExact(lockVersion), materialized, false, createdBy);
    }

    /**
     * 软删除：标记 deleteFlag 并递增乐观锁，返回已删除事实；调用方随后通过仓储 save 持久化。
     */
    public BusinessObjectDeleteEvent delete(Long expectedLockVersion) {
        requireActive();
        verifyLockVersion(expectedLockVersion);
        this.deleteFlag = true;
        this.lockVersion = Math.incrementExact(lockVersion);
        return new BusinessObjectDeleteEvent(localId, true);
    }

    public Requirement undelete(Long expectedLockVersion) {
        return restoreContent(title, description, expectedLockVersion);
    }

    public Requirement restoreContent(String title, String description, Long expectedLockVersion) {
        verifyLockVersion(expectedLockVersion);
        if (!deleteFlag) {
            throw new DomainException(RequirementResultCode.ALREADY_ACTIVE);
        }
        validateTitle(title);
        return new Requirement(localId, commonId, trackerId, title, description, revision, version,
                Math.incrementExact(lockVersion), materialized, false, createdBy);
    }

    @Override
    public LifecycleResult delete(LifecycleCommand command) {
        try {
            delete(requireExpectedLockVersion(command));
            return LifecycleResult.applied(this);
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

    private void verifyLockVersion(Long expected) {
        if (!lockVersion.equals(Objects.requireNonNull(expected, "expectedLockVersion"))) {
            throw new LockVersionConflictException(expected, lockVersion);
        }
    }
}
