package bootstrap.web;

import alm.requirement.domain.Requirement;

/** Requirement 的最小读模型。 */
public record RequirementView(String localId,
                              String commonId,
                              String trackerId,
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
                requirement.title(),
                requirement.description(),
                requirement.revision().value(),
                requirement.version().value(),
                requirement.lockVersion().value());
    }
}
