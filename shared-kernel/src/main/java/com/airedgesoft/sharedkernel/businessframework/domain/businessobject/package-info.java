/**
 * Shared Kernel — 「业务对象 + 多版次」公共能力。
 *
 * <p>
 * <b>能力来源</b>：飞书《需求的建模》简略图。图中以「物料（Part）」举例，但图上的**抽象部分**是跨 BC 共用的：
 * 需求（ALM）、物料（PLM）、文档等只要是多版次业务对象，都复用同一套版次语义。故抽象落本模块，具体聚合（如
 * {@code com.airedgesoft.alm.requirement.domain.requirement.Requirement}）留在各自 BC。
 *
 * <p>
 * <b>本包内容</b>：
 * <table border="1">
 * <caption>类型清单</caption>
 * <tr>
 * <th>类型</th>
 * <th>角色</th>
 * <th>说明</th>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable}</td>
 * <td>能力接口</td>
 * <td>可版次化能力（can-do），聚合根实现</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RevisionItem}</td>
 * <td>能力子项接口</td>
 * <td>版次记录契约（继承
 * {@link BusinessDataCarrier}：版次即业务数据承载者，但不是业务对象）</td>
 * </tr>
 * <tr>
 * <td>{@link BusinessDataCarrier}</td>
 * <td>能力接口（数据对象）</td>
 * <td>业务数据承载者——泛属性容器契约（SPI getBusinessData + 其余方法全部默认实现背靠它）；「是不是业务对象」（身份）与「能不能承载业务数据」（能力）由此解耦（用户拍板 2026-09-23）</td>
 * </tr>
 * <tr>
 * <td>{@link BusinessObject}</td>
 * <td>接口（真实业务对象）</td>
 * <td>业务对象身份契约（聚合根级）——继承 BusinessDataCarrier 取得承载能力；身份与归属成员：id / type / workSpace / workingsetId</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderTreeable}</td>
 * <td>能力接口（跨域 SPI）</td>
 * <td>可排序树节点：以父边（parentId）＋位置值（{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderIndex}）关联，落位写入口只有
 * relocateTo 一处；<b>树节点即容器成员</b>（身份 {@code getId()}，2026-11-22 由 {@code getObjectId()} 改名）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject}</td>
 * <td>值对象（只读快照）</td>
 * <td>跨聚合通用事实（{@code air_inst_common} 通用列投影），承载于
 * {@link com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectRepository}</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectRepository}</td>
 * <td>仓储端口（只读）</td>
 * <td>按身份取对象只读快照（findById／findByIds）；统一 find 家族命名（2026-11-16 由 LiteBusinessObjectService 收敛）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderTreeableRepository}</td>
 * <td>仓储端口（只读 · 跨域 SPI）</td>
 * <td>容器成员（根层／子层，含已删除）＋树结构（子树／子节点／祖先链／高度）读取；成员两法返回<b>节点自身</b> {@code List&lt;OrderTreeable&gt;}（2026-11-22删除
 * OrderMember 视图）；与 OrderTreeable／PlacementService 同族，由各 BC 聚合仓储实现（表级 SQL 落各 BC {@code *Mapper}）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.order.OrderPolicy}</td>
 * <td>落位策略（纯算法）</td>
 * <td>按容器成员批量计算位置值（head/end/between）；跨域通用，无 IO、无框架注解（2026-11 由需求域上移）</td>
 * </tr>
 * <tr>
 * <td>{@link PlacementService}</td>
 * <td>领域接缝（落位 · SPI）</td>
 * <td>算值（OrderPolicy）＋调 relocateTo 落位；跨域通用，无框架注解，由各 BC 的 {@code infrastructure/config} 装配（2026-11 由需求域上移）</td>
 * </tr>
 * <tr>
 * <td>{@link BusinessObjectImpl}</td>
 * <td>实体实现</td>
 * <td>«业务对象 类具体»——数据承载实现：只落 BusinessData 状态（LinkedHashMap 保序、同 apiId 覆盖写）+ SPI 访问器，行为全走默认实现；类名与归属随 Step 2 裁决</td>
 * </tr>
 * <tr>
 * <td>{@link FieldValue}</td>
 * <td>值对象</td>
 * <td>属性字段（含值）：<b>落库列名 column</b> + value（映射只认列名；apiId 与未用的 type 分量已于 2026-11 删除，协议 apiId ↔ 列名翻译归各 BC 词表 + 应用层）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revision}</td>
 * <td>值对象</td>
 * <td>版次（不可变）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RowRevisionInfo}</td>
 * <td>值对象</td>
 * <td>行·版次信息（版次号 + 行标记 + 原始行——仅替换/修改携带；行在版次间的追踪，各 BC 的行直接复用）</td>
 * </tr>
 * <tr>
 * <td>{@link RowMark}</td>
 * <td>枚举</td>
 * <td>行标记（失效/替换/修改/新增/不变）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.BusinessObjectBuilder}</td>
 * <td>构造类·能力基接口</td>
 * <td>创建编排模板（build＝default 装配编排：建主对象 → 版次构造＋挂载；postBuild 钩子已删，用户拍板 2026-09-23）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.RevisionBuilder}</td>
 * <td>构造类·版次构造能力</td>
 * <td>初始版次构造（DTO 驱动）+ 工作版次构造（default 自复制装配全类型统一，类型点 newRevisionItem）</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.BusinessObjectBuilderRegistry}</td>
 * <td>领域端口</td>
 * <td>按类型路由构造器</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.builder.CreateInput}</td>
 * <td>DTO</td>
 * <td>创建入参</td>
 * </tr>
 * <tr>
 * <td>{@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevalue.AttributeValue}</td>
 * <td>属性值族（attributevalue 子包）</td>
 * <td>属性值契约与 16 族值对象；校验策略族与转换工厂在
 * {@link com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation.AttributeTypeFactory}（validation
 * 子包，转换＋判定合一），详见各包说明</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>依赖纪律</b>：本包零框架技术依赖（无 ORM / 映射 / Spring 注解），只依赖 {@code lib-exception}； 且**禁止**反向依赖任何 BC。
 *
 * <p>
 * <b>迁移期例外（用户拍板 2026-09）</b>：{@code validation} 子包按「与 v3 方法保持一致直接调用，先不做改造」 口径复用旧栈元数据（{@code infrastructure-sdk} 的
 * AttributeNode / AdminCache / 校验工具），该子包因此依赖 infrastructure-sdk；待分类元数据落入 v4 后回收。其余类型维持零技术依赖不变。
 */
package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;
