/**
 * Shared Kernel — 对象关系相关公共类型与接口。
 *
 * <p>
 * <b>本包内容（2026-11-18 引用聚合重构轮定稿）</b>：
 * <table border="1">
 * <caption>类型清单</caption>
 * <tr>
 * <th>类型</th>
 * <th>角色</th>
 * <th>说明</th>
 * </tr>
 * <tr>
 * <td>{@link PropagationSuspect}</td>
 * <td>值对象（record，不可变）</td>
 * <td>传播怀疑状态：端语义（source=下游/target=上游）＋传播类型（v3 列表项 key 四值）＋两态标记＋合并基线版次（用户裁定按 v3 保留）； 标记变更返回新实例，由宿主聚合整体替换</td>
 * </tr>
 * <tr>
 * <td>{@link PropagationSuspectHolder}</td>
 * <td>角色接口</td>
 * <td>由各关系聚合实现；公共链路只认本接口</td>
 * </tr>
 * <tr>
 * <td>{@link PropagationSuspectHolderRepository}</td>
 * <td>父仓储端口</td>
 * <td>各关系仓储继承；编排经容器注入的 List 按上游／下游／行标识取行、批量存行（触发口径 8）</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>行标识</b>：{@code com.airedgesoft.sharedkernel.businessframework.domain.relationrow.RelationRowId}（relationType ＋
 * rowId）—— 见 relationrow 包 model.md。
 *
 * <p>
 * <b>触发口径（6 号稿 §三，引用侧差异见 10 号稿 §三）</b>：标记只由对象变化事件驱动（事务提交后消费）；「不传播」行在值对象内部原样返回； 只加不清、不级联；清标记只经公共应用服务。
 *
 * <p>
 * <b>依赖纪律</b>：本包零框架技术依赖（无 ORM / 映射 / Spring 注解），只依赖 {@code lib-exception}； 且**禁止**反向依赖任何 BC。
 */
package com.airedgesoft.sharedkernel.businessframework.domain.propagationsuspect;
