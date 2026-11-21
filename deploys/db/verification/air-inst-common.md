# 公共业务实例查询表

**Status: Draft**

公共表是业务实例索引。V4 的业务仓储负责将源数据映射为 `AirInstCommonPo`，并在源对象事务中通过 `lib-jdbc` 的
`JdbcCommonInstanceStore` 写入。公共表查询也由该 Bean 提供。客户旧入口继续按自己的写入协议维护公共表。

| 实例         | 分类三元组               | tracker             |
|--------------|--------------------------|---------------------|
| Working Set  | 1008 / 100801 / 10080101 | 584190236948832     |
| Tracker      | 1009 / 100901 / 10090101 | 584578917040480     |
| Folder       | 1007 / 100701 / 10070101 | 584578917040480     |
| ProductSpace | 1003 / 100301 / 10030101 | 584189545406816     |
| Requirement  | 1401 / 140101 / 14010101 | 实际所属 Tracker ID |

上述三元组由 `classifications` 预设，依次对应 `base_class_id`、`class_id`、`classification_id`。子类 ID 投影到实例的 `type`
，新建 Tracker 使用所选分类的同一子类 ID 写入 `type`、`tracker_class` 和兼容行 `sub_class_id`。产品空间初始化只为业务分类建立默认
Tracker。

共享绑定、Tracker 兼容行和需求修订快照不额外生成实例行。`air_inst_common` 是公共查询视图/缓存，不是业务权威。创建或扩展工作集时，SK
只写 LightTrackerItem 索引；业务 BC 消费 `TrackerItemsIndexedEvent`
后自行决定公共摘要的字段、编号、名称、状态和写入规则。摘要优先使用匹配来源版次的名称和版本；版次不能匹配时，不把当前来源内容写成历史摘要。摘要最终一致，失败不回滚
LTI；后续物化按同一个 LocalId 刷新该行；同族重试不会插入第二行。需求软删保留家族键，恢复刷新删除标志，永久删除移除索引行。需求
max_revision 是已知历史的查询摘要；存量回填同时读取需求快照。ProductSpace 不从挂载关系推导单一工作集，业务源未提供的字段在已有索引行上保持原值。

切换前检查全局实例 ID、工作集与 CommonId 组合键和原库 Tracker 索引。空库由 schema.sql 初始化默认根摘要，Demo 的其余公共摘要由
demo-data.sql 随业务种子数据一并写入。常规启动不补录已有业务数据。
