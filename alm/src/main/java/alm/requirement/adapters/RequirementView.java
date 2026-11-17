package alm.requirement.adapters;

import alm.requirement.domain.Requirement;

/** Requirement 的最小读模型。 */
public record RequirementView(String localId,
                              String commonId,
                              String trackerId,
                              String number,
                              String title,
                              String description,
                              int revision,
                              String version,
                              long lockVersion) {

    public static RequirementView from(Requirement requirement) {
        return new RequirementView(
                requirement.localId().value(),
                requirement.commonId().value(),
                requirement.trackerId().value(),
                requirement.number(),
                requirement.title(),
                requirement.description(),
                requirement.revision().value(),
                requirement.version().value(),
                requirement.lockVersion().value());
    }
}
