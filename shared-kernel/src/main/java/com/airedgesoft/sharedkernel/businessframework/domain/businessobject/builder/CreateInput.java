package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;

import java.util.List;

/**
 * 创建入参（DTO）。
 *
 * <p>
 * 对应飞书《需求的建模》简略图：
 *
 * <pre>
 * class CreateInput {
 *     &lt;&lt;DTO 创建入参&gt;&gt;
 *     - Long type
 *     - Long workSpace
 *     - List&lt;FieldValue&gt; fieldValues
 * }
 * </pre>
 *
 * <p>
 * 只承载「创建一个业务对象」所需的最小入参；创建人 / 创建时间等审计字段不由本 DTO 传入 （见 v4-alm RequirementBuilder 的 ⚠️ 说明：操作人来源待确认）。
 *
 * <p>
 * <b>{@code number}（编号定稿）</b>：编号随创建带入——UI 预填 / 引擎取号阶段经编号领域服务 {@code NumberService.next(type)} 取号 （v3 引擎取号即占用）后放入本
 * DTO，创建时<b>不生成、不校验</b>。<b>必填</b>：空号由应用服务先行取号，传入本 DTO 时不得为空。
 *
 * <p>
 * <b>{@code workingsetId}（工作集）</b>：<b>必填</b>——Tracker 默认链等创建期规则依赖工作集作用域（解析链见各业务策略）。
 *
 * @param type
 *            分类标识（决定由哪个具体工厂创建）
 * @param workSpace
 *            产品空间
 * @param number
 *            编号（必填；随创建带入，经创建装配通道写入聚合骨架）
 * @param workingsetId
 *            工作集 id（必填）
 * @param fieldValues
 *            初始字段值
 */
public record CreateInput(Long type, Long workSpace, String number, Long workingsetId, List<FieldValue> fieldValues) {

    public CreateInput {
        if (type == null) {
            throw new DomainException(SharedKernelResultCode.CREATE_TYPE_REQUIRED);
        }
        if (number == null || number.isBlank()) {
            throw new DomainException(SharedKernelResultCode.NUMBER_REQUIRED);
        }
        if (workingsetId == null) {
            throw new DomainException(SharedKernelResultCode.CREATE_WORKINGSET_REQUIRED);
        }
        fieldValues = fieldValues == null ? List.of() : List.copyOf(fieldValues);
    }
}
