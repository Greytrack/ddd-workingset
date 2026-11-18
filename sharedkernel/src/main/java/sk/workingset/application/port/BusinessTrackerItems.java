package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/** 当前业务 Item 的身份与存在性，由组合根接入相应业务 BC。 */
public interface BusinessTrackerItems {

    boolean existsInTracker(TrackerId trackerId);
    boolean isDeleted(LocalId localId);

    /** 事务内保护既有派生依赖的实际内容来源，直到新变体提交。 */
    boolean lockContentSource(LocalId localId);

    /** 业务 BC 提供的列表展示信息。 */
    default Optional<Presentation> presentation(LocalId localId) {
        return Optional.empty();
    }

    record Presentation(String name, String number) {}

}
