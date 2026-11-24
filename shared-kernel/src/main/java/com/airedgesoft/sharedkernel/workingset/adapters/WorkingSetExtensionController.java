package com.airedgesoft.sharedkernel.workingset.adapters;


import com.airedgesoft.lib.restful.idempotency.IdempotencyFilter;
import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetCreationService;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * T7-G：对已有子工作集批量纳入直接父 Tracker；任务复用 workingsetCreations 轮询。
 */
@RestController
@ApiResponse(responseCode = "400", description = "请求参数无效", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "资源不存在", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@RequestMapping("/v1/workingsets")
public class WorkingSetExtensionController {
    private final WorkingSetCreationService creations;

    public WorkingSetExtensionController(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    /**
     * 对已有子工作集批量纳入直接父 Tracker（T7-G），返回 202 与创建任务视图。
     */
    @Operation(operationId = "workingsetExtend", summary = "提交工作集扩展任务")
    @ApiResponse(responseCode = "202", description = "提交工作集扩展任务")
    @PostMapping("/{target}:extend")
    public ResponseEntity<WorkingSetCreationController.CreationView> extend(
            @PathVariable("target") Long target,
            @RequestHeader(name = IdempotencyFilter.KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody ExtensionRequest request) {
        String key = WorkingsetRequestKey.resolve(idempotencyKey, request.requestId());
        WorkingSetCreation creation = creations.submitExtension(target, key,
                request.trackerIds(), request.baselineAtByTracker());
        return WorkingSetCreationController.accepted(creation);
    }

    public record ExtensionRequest(
            @Schema(description = "旧客户端的幂等键", deprecated = true) String requestId,
            @ArraySchema(schema = @Schema(type = "string")) List<Long> trackerIds,
            Map<Long, Long> baselineAtByTracker) {
    }
}
