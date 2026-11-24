# bridge-v3

面向 V4 提供 V3 能力与数据转换。桥接自有接口放在对应功能包根层，调用方按接口使用。shared-kernel 已有契约直接复用。实现类使用包级可见性，接口实现方法遵循 Java 的可见性要求。

| 包 | 内容 |
|---|---|
| `button` / `button.config` / `button.model` / `button.dictionary` | 按钮配置读取、定义与快照、展示字典 |
| `classification` / `classification.metadata` / `classification.hierarchy` / `classification.model` | 分类元数据读取、层级遍历、分类范围与快照 |
| `context` / `context.request` / `context.subject` / `context.wiring` | 请求上下文同步、当前授权主体读取、桥接端口注册 |
| `criteria` / `criteria.definition` / `criteria.evaluation` | 条件配置定义与读取、条件求值与取值适配 |
| `metadata` / `metadata.cache` / `metadata.mapping` / `metadata.model` | 元数据缓存访问、V3 快照转换、通用节点模型 |
| `object` / `object.model` / `object.mapping` / `object.builder` / `object.history` / `object.schema` | 对象与行快照、对象转换、构造器兜底、操作历史、版次字段映射 |
| `privilege` / `privilege.config` / `privilege.model` / `privilege.rules` | 普通权限配置读取与过滤、权限编码和快照、业务规则读取 |
| `query` / `query.object` / `query.sql` / `query.pagination` | 对象与关联行投影、发现及权限 SQL 查询、SQL 常量和别名、分页默认值 |
| `attribute` / `table` / `revision` / `relationship` / `role` | 属性、后台表描述、版次、关联关系和角色的桥接类型与读取能力 |
| `tracker` / `folder` / `productspace` / `persistence` / `number` | 跟踪器、文件夹、产品空间、仓储和编号适配 |
| `support.error` / `support.dictionary` / `support.json` / `support.text` / `support.constants` | 错误码、枚举字典、JSON、字符串和业务常量 |

`query.ObjectProjectionReader` 负责批量读取授权所需的对象属性与完整关联行。

授权判定与组合规则由 IdentityAccess 执行，桥接侧提供配置、元数据、业务事实和数据查询能力。

实例 Bundle、缓存配置、编解码与版本协议由 IdentityAccess 持有。桥接业务投影读取在调用方事务内执行，返回属性值及页签行。

通用工具、字段和列表枚举以及业务常量通过薄适配复用 V3 实现。JSON 复用 V3 ObjectMapper，桥接入口将序列化/解析失败按技术故障抛原始非受检异常（保留原因链）。桥接边界把 legacy 异常转译为 `DomainException`（本模块 `BridgeResultCode`，模块号 020）或原始非受检异常，不透传也不拷贝 legacy 异常类型。

共同配置只向 V4 投影普通角色、权限和条件。V3 模板与工作区实例由读取边界过滤；请求上下文按普通角色重新装载有效授权。

`privilege.model.Privilege` 为 V3 `PrivilegeTypeEnum` 的只读桥接名称，所有权限编码、属性与权限集合均委托原定义。`button.config` 保留 V3 按钮配置桥接供按钮调用方使用，IdentityAccess 授权装配不依赖按钮桥接。
