package com.airedgesoft.alm.requirement.adapters;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.domain.Requirement;

/**
 * Requirement 的最小读模型。
 */
public record RequirementView(String localId,
                              String commonId,
                              String trackerId,
                              String number,
                              String title,
                              String description,
                              int revision,
                              String version,
                              long lockVersion, boolean materialized, boolean checkedOut, String flags) {

    public static RequirementView from(Requirement requirement) {
        return new RequirementView(
                Long.toString(requirement.localId()),
                Long.toString(requirement.commonId()),
                Long.toString(requirement.trackerId()),
                requirement.number(),
                requirement.title(),
                requirement.description(),
                requirement.revision(),
                requirement.version(),
                requirement.lockVersion(), requirement.hasMaterialized(), requirement.checkedOut(), requirement.flags());
    }

    public static RequirementView from(RequirementDetail detail) {
        return new RequirementView(
                String.valueOf(detail.localId()),
                String.valueOf(detail.commonId()),
                String.valueOf(detail.trackerId()),
                detail.number(),
                detail.title(),
                detail.description(),
                detail.revision(),
                detail.version(),
                detail.lockVersion(), detail.materialized(), detail.checkedOut(), detail.flags());
    }
}
