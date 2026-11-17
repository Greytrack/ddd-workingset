---
feature: login-page-config-isolation
requirement_doc: .lattice/requirements/features/login-page-config-isolation.md
created: "2026-07-08"
---
# 登录页配置独立存储

将 `loginPageConfig` 从 `air_sys_property.value` JSON 中拆出，存到独立表，解决升级脚本覆盖用户配置的问题。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-08 | 用独立表 `air_sys_login_page_config` 存储 loginPageConfig，仅新表优先/空回退策略过渡 | 避免修改 OAuth 节点 JSON（影响面太大）；独立表升级脚本不再触碰 | 拆整个 client_config、改 JSONB+jsonb_set |
| 2026-07-08 | 回退读 node JSON 时不自动写入新表 | GET 是只读操作，保持无副作用 | 读时自动同步写入新表 |
| 2026-07-08 | 升级迁移用 SQL 不用 Java | 简单可追溯，与现有 Flyway 风格一致 | Java migration |
| 2026-07-08 | Repository 放在 `infrastructure/sdkImpl/oauth/` | 与 `ClientDetailsRepository` 同包，Biz 层可通过注入使用 | 放 infrastructure/biz |

## Open Questions

## Constraints

- 新表 `id` 固定为 `1`（单行配置）
- `PreferenceManagement` 的 PUT 路径不再经过 `GenericOauthClient.updateClientConfig()`
- 迁移 SQL 必须幂等（`WHERE NOT EXISTS`）
- node JSON 中 `client_config.loginPageConfig` 保留不动（过渡期回退 + 不被本功能删除）

## Key Files

| 文件 | 类型 | 说明 |
|------|------|------|
| `infrastructure/sdkImpl/.../po/LoginPageConfigPO.java` | 新建 | MyBatis-Plus 实体 |
| `infrastructure/sdkImpl/.../persistence/ILoginPageConfigPersistence.java` | 新建 | BaseMapper |
| `infrastructure/sdkImpl/.../oauth/LoginPageConfigRepository.java` | 新建 | 仓储 |
| `infrastructure/biz/.../settings/admin/management/PreferenceManagement.java` | 修改 | 读写切换 |
| `upgrade/.../V3.5.x__login_page_config_isolation.sql` | 新建 | DDL+迁移 |

## Design: Level 1 — Capabilities

已由需求规格的 5 个 Scenario 完整定义，从 Level 2 开始切入：

1. 升级脚本迁移已有 loginPageConfig 到新表（幂等）
2. 读取时新表优先，空则回退 node JSON
3. 写入时写入新表，不同步回 node JSON
4. 后续升级脚本不触碰新表

## Design: Level 2 — Components

### 组件图

```
┌──────────────────────────────────────────────────────┐
│                    upgrade/                           │
│  V3.5.x__login_page_config_isolation.sql             │
│  • CREATE TABLE + INSERT 迁移 + 默认值初始化           │
└──────────────────────┬───────────────────────────────┘
                       │ Flyway
                       ▼
┌──────────────────────────────────────────────────────┐
│              PostgreSQL                               │
│  air_sys_login_page_config (新)  │  air_sys_property  │
│  id=1, config_json(JSONB)        │  id=36471456 (旧)  │
└──────┬───────────────────────────┴──────┬────────────┘
       │                                  │ (回退读)
       ▼                                  ▼
┌──────────────────┐              ┌──────────────────────┐
│ sdkImpl/oauth/   │              │ biz/settings/admin/  │
│ LoginPageConfig  │── 注入 ──→  │ PreferenceManagement │
│ Repository       │              │ (modified)           │
└──────────────────┘              └──────────────────────┘
```

| # | 组件 | 层 | 职责 |
|---|------|-----|------|
| 1 | Flyway migration | upgrade | 建表、迁移数据、默认值初始化 |
| 2 | LoginPageConfigRepository | SDKImpl | 封装新表 CRUD（MyBatis-Plus） |
| 3 | PreferenceManagement (modified) | Biz | 读优先新表、写新表、回退 node |

依赖方向：Biz → SDKImpl（允许）。无跨组件依赖，无循环。

## Design: Level 3 — Interactions

### 读取

```
GET /detail → PreferenceManagement.getPageConfig()
  → LoginPageConfigRepository.findById(1)
    ├── 有数据 → 返回
    └── 无数据 → genericOauthClient.getClientInfo().clientConfig.loginPageConfig
                  (不写入新表，等 PUT 时写入)
```

### 写入

```
PUT /preference → PreferenceManagement.update(cfg)
  → LoginPageConfigRepository.findById(1)
    ├── 有数据 → update(cfg)
    └── 无数据 → insert(id=1, cfg)
  → 不再调用 genericOauthClient.updateClientConfig()
```

### 迁移（升级脚本，一次执行）

```
Flyway 执行:
  1. CREATE TABLE IF NOT EXISTS
  2. INSERT ... SELECT ... FROM air_sys_property WHERE id=36471456
     WHERE NOT EXISTS (新表已有数据)
  3. INSERT 默认值 WHERE NOT EXISTS
```

步骤 2 和 3 互斥；`WHERE NOT EXISTS` 保证幂等。

## Design: Level 4 — Contracts

### LoginPageConfigPO

```java
@Data
@TableName("air_sys_login_page_config")
public class LoginPageConfigPO {
    @TableId private Long id;
    private String configJson;
    private Long createdBy;
    private Long createdTime;
    private Long updatedBy;
    private Long updatedTime;
}
```

### ILoginPageConfigPersistence

```java
@Mapper
public interface ILoginPageConfigPersistence extends BaseMapper<LoginPageConfigPO> {}
```

### LoginPageConfigRepository

```java
public class LoginPageConfigRepository {
    ClientLoginPageConfig findLoginPageConfig();  // 返回 null 当表为空
    void insert(ClientLoginPageConfig config);
    void update(ClientLoginPageConfig config);
}
```

### PreferenceManagement（修改）

```java
// 新增注入
private final LoginPageConfigRepository loginPageConfigRepository;

// 读：新表优先 → 空则回退
public ClientLoginPageConfig getPageConfig() {
    ClientLoginPageConfig config = loginPageConfigRepository.findLoginPageConfig();
    return config != null ? config
        : genericOauthClient.getClientInfo().getClientConfig().getLoginPageConfig();
}

// 写：只写新表，不走 node
@Transactional
public String update(ClientLoginPageConfig cfg) {
    if (loginPageConfigRepository.findLoginPageConfig() == null) {
        loginPageConfigRepository.insert(cfg);
    } else {
        loginPageConfigRepository.update(cfg);
    }
    return AirEdgeConstants.SUCCESS;
}
```

## Design Summary

- **组件**：3 个（SQL 迁移 + Repository + PreferenceManagement 修改）
- **层**：upgrade / SDKImpl / Biz，遵循现有架构分层
- **关键接口**：`LoginPageConfigRepository.findLoginPageConfig()`、`insert()`、`update()`
- **架构约束**：Biz → SDKImpl（允许），不经过 Node.update 写 property
- **域模型**：无新增聚合根，loginPageConfig 是纯配置值对象，不属于 DataObject 框架
- **设计状态**：Approved — ready for implementation
