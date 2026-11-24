package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder;

import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.FieldValue;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revision;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable;

import java.util.Collection;

/**
 * 版次构造能力（构造类 · 能力扩展）——<b>创建期</b>初始版次的产出方。
 *
 * <p>
 * 对应 14 需求建模 / 08 多版次能力 新类图 {@code <<interface 构造类 · 版次构造能力>>}：
 *
 * <pre>
 * class RevisionBuilder {
 *     + buildRevisionItem(createDto) RevisionItem
 * }
 * </pre>
 *
 * <p>
 * <b>职责边界</b>：版次记录的创建期构造统一归本能力（由具体构造器实现，原独立版次工厂并入具体构造器）—— {@link #buildRevisionItem(CreateInput)}（DTO 驱动，抽象方法）建初始版次；
 * 创建期系统字段装配（编号 / Tracker 默认链等）由具体构造器在本方法内完成。 签出期的工作版次不在本能力——由聚合根经 {@link Revisionable#newWorkingRevision(RevisionItem)}
 * 产出（聚合内部实体工厂）。
 *
 * @param <T>
 *            聚合根类型
 * @param <R>
 *            具体版次记录类型
 */
public interface RevisionBuilder<T extends BusinessObject, R extends RevisionItem> extends BusinessObjectBuilder<T> {

    /**
     * 创建期版次构造：建初始版次（版次号 1、未签出、未签入），业务数据取自创建入参； 创建期系统字段装配（编号 / Tracker 默认链等）由具体构造器在此完成。
     *
     * @param createInput
     *            创建入参
     */
    R buildRevisionItem(CreateInput createInput);

    /**
     * 类型点（具体构造器供给）：按原料构造具体版次记录（身份分配 / 具体类实例化）。
     *
     * @param fieldValues
     *            版次业务数据
     * @param revision
     *            版次值对象
     */
    R newRevisionItem(Collection<FieldValue> fieldValues, Revision revision);
}
