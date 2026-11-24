/**
 * 属性值校验（validation 子包）。
 *
 * <p>
 * 值校验家族落本子包（2026-11-21 用户裁定：校验与值对象分离）：值对象拆到 {@code businessobject.attributevalue}， 本子包承载全部校验职责：
 *
 * <ul>
 * <li>{@link AttributeValidatorStrategy}——校验策略契约（无状态、按类型取单例）；</li>
 * <li>14 个 {@code XxxValidatorStrategy}——逐类型校验规则实现（必填 / 文本规则 / 长度 / 级联范围 / 图片 / 起止日期等）；</li>
 * <li>{@link AttributeTypeFactory}——「串
 * → 值」与「值校验」合成唯一入口（转换＋判定合一）；</li>
 * <li>{@link AttributeValueValidationContext}——单字段校验上下文（短命对象，转发策略）；</li>
 * <li>{@link AttributeValidationSupport}——平台取数＋校验收口点（v3 取数经
 * {@link AttributeValueMetadataPort} 收口，实现落 {@code v4-bridge-v3}）；</li>
 * <li>{@link Violation}——违例承载（原
 * ResCode + args 映射）；</li>
 * <li>{@link AttributeObjectState}——同对象属性值状态（级联跨字段判定用，null＝无法判定放行）；</li>
 * <li>{@link CascadeValueRangeChecker}——级联从值范围校验（允许集解析为公共口径）。</li>
 * </ul>
 *
 * <p>
 * 原 {@code Specification} / {@code ValueSpecification} / {@code AttributeValueSpecification} /
 * {@code AttributeSpecifications} 及 13 个 {@code *ValueSpecification} 随「生成即校验、命中首条即抛」定稿一并下线。
 *
 * <p>
 * <b>取数口径（照旧，经桥）</b>：AttributeNode / 列表对象 / 文本规则 / 级联关系等 v3 取数语句 1:1 镜像进
 * {@link AttributeValueMetadataPort}（实现落 {@code v4-bridge-v3}，装配期安装 {@link AttributeValueMetadataPorts}），
 * 判定语义与 v3 直调完全一致，本包零 v3 依赖。
 */
package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;
