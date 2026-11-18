package alm.requirement.application.query.model;

/** Requirement 查询读模型；ID 保持数值类型。 */
public record RequirementDetail(Long localId, Long commonId, Long trackerId, String number,
                                String title, String description, int revision, String version,
                                long lockVersion, boolean deleted) {
}
