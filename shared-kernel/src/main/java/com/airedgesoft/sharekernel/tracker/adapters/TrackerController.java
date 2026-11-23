package com.airedgesoft.sharekernel.tracker.adapters;


import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.tracker.application.command.TrackerCommandService;
import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * Tracker 的 HTTP 入口：创建业务原生 Tracker、重命名与删除。
 * 删除顺序与拒绝语义由 {@link TrackerCommandService} 承担，控制器只做参数校验与映射。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TrackerController {
    private final TrackerCommandService commands;

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, field + " must not be blank");
        }
        return value;
    }

    /**
     * 在默认工作集创建业务原生 Tracker；名称在（产品空间，分类）内唯一，共享方式须与分类相容（T2 §4.4）。
     */
    @PostMapping("/worksets/{workingSetId}/trackers")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerView create(@PathVariable Long workingSetId, @RequestBody CreateTrackerRequest request) {
        SharingMode mode = SharingMode.valueOf(required(request.sharingMode(), "sharingMode"));
        return TrackerView.from(commands.create(workingSetId,
                Long.toString(Objects.requireNonNull(request.productSpaceId(), "productSpaceId")), required(request.name(), "name"),
                Long.toString(Objects.requireNonNull(request.classificationId(), "classificationId")), mode));
    }

    /**
     * 重命名 Tracker；名称去首尾空白后须为 1–255 字符。
     */
    @PatchMapping("/trackers/{trackerId}")
    public TrackerView rename(@PathVariable Long trackerId, @RequestBody RenameTrackerRequest request) {
        return TrackerView.from(commands.rename(trackerId, request.name()));
    }

    /**
     * 删除 Tracker；须通过无 Item、无组织子级、无派生子级三项检查（T2 K2）。
     */
    @DeleteMapping("/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long trackerId) {
        commands.delete(trackerId);
    }

    public record CreateTrackerRequest(Long productSpaceId, String name, Long classificationId,
                                       String sharingMode) {
    }

    public record RenameTrackerRequest(String name) {
    }

    public record TrackerView(String trackerId, String name, String parentId, String sourceId, String productSpaceId,
                              String classificationId, String sharingMode, boolean isDefault) {
        static TrackerView from(Tracker tracker) {
            return new TrackerView(Long.toString(tracker.trackerId()), tracker.name(),
                    tracker.parentId() == null ? null : Long.toString(tracker.parentId()),
                    tracker.sourceId() == null ? null : Long.toString(tracker.sourceId()),
                    tracker.productSpaceId().toString(), tracker.classificationId(), tracker.sharingMode().name(),
                    tracker.isDefault());
        }
    }
}
