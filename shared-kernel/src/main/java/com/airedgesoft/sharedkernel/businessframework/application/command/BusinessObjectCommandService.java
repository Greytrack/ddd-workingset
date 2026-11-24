package com.airedgesoft.sharedkernel.businessframework.application.command;

import com.airedgesoft.sharedkernel.businessframework.domain.tracker.TrackerService;

/**
 * 业务对象写应用服务（父接口 · 公共层）：跨 BC 复用的创建期编排动作。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：下列动作与具体 BC 无关——跟踪分类默认链解析（{@link TrackerService}）与分类属性 schema 按落库列名建映射
 * （{@link AttributeSchemaSource}）是所有业务对象创建 / 修改都要走的基础设施规则；各 BC 的应用服务实现本接口后直接复用，不各自重复实现。
 *
 * <p>
 * <b>端口以入参传入</b>：本接口不持状态、不注入端口——{@code trackerService} / {@code attributeSchemaSource} 由调用方（各 BC 的应用服务）
 * 传入，既可复用同一实现，也便于单测直接以桩替身调用（端口为 {@code null} 时按「只取显式值 / 空元数据」降级）。
 */
public interface BusinessObjectCommandService {

    /**
     * 跟踪分类默认链解析：显式传值 → 工作集默认分支 → 兜底（旧栈 {@code prepareAndCheckTracker} 口径）。
     *
     * @param trackerService
     *            跟踪分类端口（可空——空时只取显式传值，供无 tracker 能力的场景 / 单测使用）
     * @param workingsetId
     *            工作集 id（可空，走解析链）
     * @param workSpace
     *            产品空间（可空——为空时走全局 Tracker 分支）
     * @param explicitTrackerId
     *            入参跟踪分类 id（可空——为空走默认链）
     * @param type
     *            业务对象分类 type
     * @return 最终跟踪分类 id
     */
    default Long resolveTrackerId(TrackerService trackerService, Long workingsetId, Long workSpace,
            Long explicitTrackerId, Long type) {
        if (trackerService == null) {
            return explicitTrackerId;
        }
        return trackerService.resolve(workingsetId, workSpace, explicitTrackerId, type);
    }
}
