package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder;

import java.lang.annotation.*;

/**
 * 业务对象构造器注册注解：声明该构造器承接的分类 type（分类树节点 id）。
 *
 * <p>
 * 镜像旧空间《DataObject 注册机制》（backend {@code infrastructure/sdkImpl} 的 {@code @RegisterDataObject} +
 * {@code DataObjectRegistry} + {@code DataObjectRegistryInitializer} 三件套），v4 侧两处改造：
 *
 * <ul>
 * <li><b>注册对象是构造器 Bean 实例</b>：v4 注册中心契约是 {@code register(type, builder)}（应用层按创建入参 type 路由到构造器）， 不再是旧空间的 DataObject 子类
 * Class；注册实例由 Spring 容器提供（构造器承载端口，见下）；</li>
 * <li><b>分类范围显式列全</b>：旧空间一个注解经 AdminCache 元数据扇出到整棵子树（子类 P1 &gt; 类 P2 &gt; 基类 P3）； v4 分类元数据尚未独立成模块，故本注解把构造器承接的
 * <b>全部</b>分类节点 id（基类 / 类 / 子类）显式列出——语义等价。分类元数据模块落地后， 本注解可收敛为只标基类、由扇出逻辑展开，届时注册口径回归旧空间。</li>
 * </ul>
 *
 * <p>
 * <b>声明位置（用户拍板 2026-10-13：构造器一律 Spring Bean，注册读容器实例）</b>：
 *
 * <ul>
 * <li>构造器实现类上（{@link ElementType#TYPE}）——最直观；</li>
 * <li>或装配侧 {@code @Bean} 工厂方法上（{@link ElementType#METHOD}）——分类坐标留在装配配置，构造器（领域类）不持注册坐标。</li>
 * </ul>
 *
 * 两者等效、类上优先；注册由启动侧（deploy-full {@code BusinessObjectBuilderRegistrar}）在单例实例化完成后扫描容器内 {@link BusinessObjectBuilder} Bean
 * 读注解完成—— <b>不再反射新建实例</b>（构造器的端口由容器注入），也不等 {@code ApplicationReadyEvent}（消除「已可收请求而注册未完成」窗口）。 本注解与注解值常量均无框架依赖，领域模块保持零
 * Spring 依赖。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RegisterBusinessObjectBuilder {

    /**
     * 构造器承接的分类 type 集合（分类树节点 id，与旧空间 {@code AirConstants.*} 的 BASE / CLASS / SUBCLASS ID 同源）。
     */
    long[] value();
}
