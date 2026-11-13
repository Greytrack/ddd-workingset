package alm.requirement.infrastructure;

import java.util.Map;
import java.util.Objects;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.Caller;
import sk.workingset.application.CapabilitySet;
import sk.workingset.application.FailureReason;
import sk.workingset.application.Operation;
import sk.workingset.application.TrackerItemContent;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/**
 * ALM 侧的 Adapter：实现 SK 定义的只读应用层 Port（T4 §3.1 两层接口并存）。
 *
 * <p>构造时绑定一个变体身份（{@code LocalId}）；SK 只通过该 Port 读取内容，不接触 Requirement 聚合类型。
 */
public class RequirementAdapter implements TrackerItemReadPort {

    private final RequirementRepository repository;
    private final LocalId localId;

    public RequirementAdapter(RequirementRepository repository, LocalId localId) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.localId = Objects.requireNonNull(localId, "localId");
    }

    @Override
    public TrackerItemContent readAt(Revision revision) {
        Requirement requirement = find();
        if (requirement.deleted()) {
            throw new ApplicationFailure(FailureReason.DELETED, "requirement deleted: " + localId.value());
        }
        return contentAt(requirement, revision);
    }

    @Override
    public TrackerItemContent readInheritedAt(Revision revision) {
        Requirement requirement = find();
        if (requirement.deleted()) {
            throw new ApplicationFailure(FailureReason.DELETED, "requirement deleted: " + localId.value());
        }
        return contentAt(requirement, revision);
    }

    @Override
    public ContentEvidence evidence() {
        Requirement item = find();
        return new ContentEvidence(item.commonId(), item.revision(), item.version().value(),
                item.lockVersion().value(), item.deleted());
    }

    private TrackerItemContent contentAt(Requirement requirement, Revision revision) {
        if (!requirement.revision().equals(revision)) {
            throw new ApplicationFailure(FailureReason.HISTORY_UNAVAILABLE,
                    "required revision is unavailable: " + revision.value());
        }
        Map<String, Object> fields = Map.of(
                "title", requirement.title(),
                "description", requirement.description());
        return new TrackerItemContent(requirement.revision(), fields);
    }

    @Override
    public Revision revision() {
        return find().revision();
    }

    @Override
    public CapabilitySet capabilities() {
        return CapabilitySet.full();
    }

    @Override
    public boolean authorize(Operation operation, Caller caller) {
        // S1 不接入权限平台；恒允许，权限模型留后续切片
        return true;
    }

    private Requirement find() {
        return repository.findById(localId)
                .orElseThrow(() -> new ApplicationFailure(
                        FailureReason.NOT_FOUND, "requirement not found: " + localId.value()));
    }
}
