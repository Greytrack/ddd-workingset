package alm.requirement.application;

import sk.workingset.application.port.BusinessObjectType;

import java.util.Set;

/** 由 Requirement 业务能力支持的 Tracker 分类。 */
public final class RequirementTypes {
    public static final Set<BusinessObjectType> SUPPORTED = Set.of(
            new BusinessObjectType(14010101L), new BusinessObjectType(14010102L));

    private RequirementTypes() {
    }
}
