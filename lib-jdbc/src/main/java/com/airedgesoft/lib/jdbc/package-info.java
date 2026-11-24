/**
 * 聚合 JDBC 持久化流程：仓储决定如何加载领域对象和生成计划，本包协调事务内身份、CAS 与 SQL 执行。
 *
 * <p>
 * 从 {@link AggregateMapping} 阅读接入配置：onLoad 接收原始快照并返回领域对象， onCreate 返回插入计划，onUpdate
 * 只接收领域终态，由仓储重新读库并核验对象携带的旧版本，返回更新或无变化计划。 三个 hook 直接指向仓储方法，不强制投影、贡献、审计等内部步骤。
 *
 * <p>
 * {@link JdbcWritePlanner} 是可选纯工具，供仓储复用列差异、复制和审计归并； 也可以直接构造计划。它不注册 hook、不重建领域对象、不执行 SQL。
 * {@link JdbcRowMapping} 共用列类型与字段提取声明。
 *
 * <p>
 * 软删除写路径为<b>仓储显式语句</b>，无 SQL 自动改写通道：裸 JdbcTemplate 写模型走 {@link JdbcSoftDelete}
 * （列模型由调用方声明）；Spring Data JDBC 写模型走 {@link AggregateSoftDelete}（表 / 主键 / 版本列取自实体元数据， 软删 /
 * 恢复同语句推进版本）。删除语义是领域决策，由仓储语句明说；读取侧的存活过滤注入与请求级放行属查询通道关注点，不在本库职责内。
 *
 * <p>
 * 应用服务开启外层事务，仓储加载聚合，领域执行业务动作，最后由 Store 保存。 Store 以「本事务是否加载过该身份」判断新增或更新——更新既有聚合必须先在同一事务内加载（加载快照即 CAS 基线）；Store
 * 不缓存原始行，仓储负责在回读时保留 SQL NULL、身份及锁版本。 Store 以映射实例、聚合身份和对象引用隔离记录，同一对象每个事务只保存一次。
 *
 * <p>
 * Store 按加载判定分派创建或更新 hook，得到计划后执行；更新先竞争根版本，再执行有序行操作。 NoChanges 不执行写入 SQL。保存失败使当前持久化上下文失效并标记事务回滚；Receipt 不代表外层事务已提交。
 * 执行器采用 PostgreSQL 方言，日志不输出业务参数。
 */
package com.airedgesoft.lib.jdbc;
