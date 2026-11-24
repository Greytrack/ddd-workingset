package com.airedgesoft.sharedkernel.businessframework.infrastructure.reference;

/**
 * 引用表列名常量（{@code air_inst_tab_reference}；与 v3 同列）——基础设施 SSOT，领域层不见列名。
 */
final class ReferenceStorage {

    static final String ID = "id";
    static final String COMMON_ID = "common_id";
    static final String INSTANCE_ID = "instance_id";
    static final String INSTANCE_TYPE = "instance_type";
    static final String ATTRIBUTE_API_ID = "attribute_api_id";
    static final String INSTANCE_REVISION_ID = "instance_revision_id";
    static final String PROPAGATION_TYPE = "propagation_type";
    static final String DOWNSTREAM_MERGE_REVISION = "downstream_merge_revision";
    static final String DOWNSTREAM_CHANGED = "downstream_changed";
    static final String RELATED_INSTANCE_ID = "related_instance_id";
    static final String RELATED_COMMON_ID = "related_common_id";
    static final String RELATED_TYPE = "related_type";
    static final String RELATED_WORKINGSET_ID = "related_workingset_id";
    static final String RELATED_REVISION_ID = "related_revision_id";
    static final String UPSTREAM_CHANGED = "upstream_changed";
    static final String REFERENCE_TYPE = "reference_type";
    static final String UPSTREAM_MERGE_REVISION = "upstream_merge_revision";
    static final String TAB_ID = "tab_id";
    static final String FLAGS = "flags";
    static final String REVISION_IN = "revision_in";
    static final String REVISION_OUT = "revision_out";
    static final String EDIT_ROW = "edit_row";
    static final String RELATED_REVISION = "related_revision";
    static final String WORK_SPACE = "work_space";
    static final String RELATED_WORK_SPACE = "related_work_space";
    static final String WORKINGSET_ID = "workingset_id";
    static final String TRACKER = "tracker";
    static final String RELATED_TRACKER = "related_tracker";
    static final String LOCK_VERSION = "lock_version";
    static final String CREATED_BY = "created_by";
    static final String CREATED_TIME = "created_time";
    static final String UPDATED_BY = "updated_by";
    static final String UPDATED_TIME = "updated_time";

    static final String TABLE = "air_inst_tab_reference";

    private ReferenceStorage() {
    }

    /** 列名集中声明（供 Mapper / RepositoryImpl 引用；编译期一致性）。 */
    static final class ReferenceColumns {
        private ReferenceColumns() {
        }

        static final String ID = ReferenceStorage.ID;
        static final String INSTANCE_ID = ReferenceStorage.INSTANCE_ID;
        static final String ATTRIBUTE_API_ID = ReferenceStorage.ATTRIBUTE_API_ID;
        static final String PROPAGATION_TYPE = ReferenceStorage.PROPAGATION_TYPE;
        static final String DOWNSTREAM_MERGE_REVISION = ReferenceStorage.DOWNSTREAM_MERGE_REVISION;
        static final String DOWNSTREAM_CHANGED = ReferenceStorage.DOWNSTREAM_CHANGED;
        static final String RELATED_INSTANCE_ID = ReferenceStorage.RELATED_INSTANCE_ID;
        static final String RELATED_REVISION_ID = ReferenceStorage.RELATED_REVISION_ID;
        static final String UPSTREAM_CHANGED = ReferenceStorage.UPSTREAM_CHANGED;
        static final String REFERENCE_TYPE = ReferenceStorage.REFERENCE_TYPE;
        static final String UPSTREAM_MERGE_REVISION = ReferenceStorage.UPSTREAM_MERGE_REVISION;
        static final String REVISION_IN = ReferenceStorage.REVISION_IN;
        static final String REVISION_OUT = ReferenceStorage.REVISION_OUT;
        static final String RELATED_REVISION = ReferenceStorage.RELATED_REVISION;
    }
}
