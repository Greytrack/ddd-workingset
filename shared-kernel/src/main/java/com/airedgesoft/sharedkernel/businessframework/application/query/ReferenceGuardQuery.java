package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.sharedkernel.businessframework.domain.reference.Reference;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceGuardDomainService;

import java.util.List;

/**
 * 引用守卫事实读端口（应用读端口：仅取事实不判定，SKILL 6.5-20）。
 *
 * <p>
 * 由引用写用例（各 BC 的 {@code ReferenceCommandService}）装配调用：引用方编辑事实（版次/签出态/删除位）、既有有效行、 目标存在性分别从源对象 BC、引用现表与 lite
 * 业务对象快照取数。领域侧守卫（{@link ReferenceGuardDomainService}）无 IO，事实在此预取。
 *
 * <p>
 * <b>归属</b>：shared-kernel 公共域——引用可跨业务对象/业务线挂出，守卫事实读口径各 BC 一致； 其中「引用方编辑事实」依赖源对象 BC 的私有聚合，经 {@link ReferenceEditorFacts}
 * 端口回注（依赖倒置，shared-kernel 不反向依赖任何 BC）。
 */
public interface ReferenceGuardQuery {

    /**
     * 引用方编辑事实（版次/签出态/删除位）。
     *
     * @param sourceId
     *            引用方对象 id
     * @return 编辑事实
     */
    ReferenceGuardDomainService.EditorFacts loadEditorFacts(Long sourceId);

    /**
     * 本属性本版次的既有有效行（判重与整值替换依据；历史求值在读侧，口径 11）。
     *
     * @param sourceId
     *            引用方对象 id
     * @param attributeApiId
     *            引用属性 apiId
     * @param revision
     *            引用方版次号
     * @return 有效行集合
     */
    List<Reference> loadEffectiveRows(Long sourceId, String attributeApiId, int revision);

    /**
     * 目标对象事实（存在 / 已删——新增守卫用；裁决 1 只影响删除侧，新增仍校验目标存在）。
     *
     * @param targetIds
     *            目标对象 id 集合
     * @return 存在且未删除的 id 集合
     */
    List<Long> loadExistingTargetIds(List<Long> targetIds);
}
