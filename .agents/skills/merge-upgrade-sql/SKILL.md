---
name: merge-upgrade-sql
description: airedge 发版升级脚本合并——把 versionupdates.vX.Y 目录下 0.x 补丁脚本语义合并为一个 V(X.(Y+1))__upgradesql.sql（或下一个可用版本号）：最前加固定头、文件名含 fix_upgradesql 的脚本不合并而是原样附加到末尾，并用「双库对比生成 + 独立验证收敛 + pg_dump 终审」三道门禁保证与逐脚本执行终态零差异。Use when the user says 合并v3.7 / 合并发版脚本 / merge upgrade / versionupdates 合并, or asks to squash Flyway migration scripts for a release.
---

# merge-upgrade-sql: 发版升级脚本合并

把 `backend/upgrade/src/main/resources/flyway/migration/versionupdates.v{X.Y}/` 下的 0.x 脚本语义合并为单个
`V{X.Y}.N__upgradesql.sql`，并对「逐脚本执行终态」与「合并脚本执行终态」做**结构+数据零差异**验证。

两条宿主侧拼装规则（不由 dbdiff 生成，故必须显式处理）：

- **固定头**：产物**最前面**插入一段逐字固定的 SQL（默认：动态 DROP `public.air_inst_view_ws_*` 视图），后面内容原封不动
- **fix 脚本**：文件名含 `fix_upgradesql` 的脚本**不参与语义合并，原文件逐字保留、放到产物最后**

## 触发与定位

用户说「合并v3.7」「merge upgrade 3.7」等：

1. 目标目录 = `backend/upgrade/src/main/resources/flyway/migration/versionupdates.v3.7/`（文件夹命名
   `versionupdates.v{major}.{minor}`）
2. **from 基线** = 除目标目录外全部脚本的最高版本（如 V3.6.0）；**to 产物版本** = `{major}.{minor}.N`，N
   取第一个不存在于目录且大于目录内最高版本的自然数（首次合并即 3.7.1）
3. 目录不明确时先 `ls` migration 目录再向用户确认

## 前置检查（运行前 agent 必须确认）

- **Docker Desktop 在运行**；三个镜像**默认直接拉阿里云固定版本**（驱动参数可覆盖；本地若只有旧名字会自动回退并提示改名）：

  | 用途                       | 默认引用                                                            | 备注                                                                                       |
  |----------------------------|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
  | 对比生成                   | `registry.cn-hangzhou.aliyuncs.com/plm/dbdiff:3.0.0-rc.12`          | **必须钉 rc.12**（方向语义/撇号转义均基于此版本实测；`latest` 已到 rc.34，升级需重验方向） |
  | 收敛验证                   | `registry.cn-hangzhou.aliyuncs.com/plm/postgrelcomparer:distroless` | 官方**仅 arm64** 构建                                                                      |
  | psql/pg_dump/createdb      | `registry.cn-hangzhou.aliyuncs.com/plm/postgres16:16-alpine`        | —                                                                                          |
  | 跑 arm64 comparer 的模拟器 | `registry.cn-hangzhou.aliyuncs.com/plm/binfmt:latest`               | 仅 **amd64 宿主**需要：`docker run --privileged --rm <该镜像> --install arm64`             |

  旧名字（`ghcr.io/dbdiff/dbdiff:3.0.0-rc.12`、`fancylk/postgrelcomparer:distroless`、`postgres:16-alpine`）仍可用，驱动会自动回退并提示
  `docker tag`。外网拉不到时可用镜像站 `ghcr.nju.edu.cn` / `docker.1panel.live` 再 `docker tag` 成默认引用。驱动预检会实际探活三个镜像（缺
  psql/pg_dump、平台不匹配都会在开工前报错并给出修复命令）
- `backend/upgrade/bin/migrate_windows_amd64.exe` 存在
- 目标目录无 Java 迁移类（`src/main/java/com/airedgesoft/ae/upgrade/V{M}_{N}*.java`）——**Java 迁移无法被合并**，有则停下来问用户
- 目标目录中**文件名含 `fix_upgradesql`** 的脚本不参与语义合并，会被逐字附加到产物末尾；前提是这些脚本**幂等**
  （可重复执行零变更）。不幂等则 STEP 6.8 直接 FAIL
- 产物固定头（置于产物最前）默认使用脚本内置的 `$HeaderSqlDefault`（动态 DROP `public.air_inst_view_ws_*` 视图）；需换头时用
  `-HeaderFile <path>` 覆盖，并在交付说明中显式告知用户
- 产物 `V{to}__upgradesql.sql` 尚不存在（存在则问用户是否重跑，重跑前先移走旧产物）
- **重跑前必须恢复已归档的 0.x 脚本**：成功后没成功都会把目标目录的 0.x 注入 `migration_archive/<folder>/`，重跑前先挑回来（
  `cp migration_archive/<folder>/*.sql <folder>/`），否则会报「目标目录没有任何 V*.sql 脚本」
- 本机开发库连接串（PG 16.10 @ 127.0.0.1:5432，用户 airedge）：密码从 `backend/upgrade/scripts/.env` 读取，或向用户索取；不要硬编码在脚本里

## 运行

```powershell
& '<repo>\.agents\skills\merge-upgrade-sql\merge-upgrade.ps1' `
  -Folder versionupdates.v3.7 `
  -PgBase  'postgres://<user>:<pass>@127.0.0.1:5432/postgres' `
  [-RepoUpgrade '<backend/upgrade 绝对路径>'] [-NoArchive] [-Keep] [-MaxRounds 3] `
  [-HeaderFile '<固定头 sql 文件>'] [-FixPattern 'fix_upgradesql'] `
  [-PgImage '<postgres 镜像>'] [-ComparerImage '<comparer 镜像>'] [-DbdiffImage '<dbdiff 镜像>'] [-BinfmtImage '<binfmt 镜像>']
```

- `-RepoUpgrade` 省略时按脚本位置自动推断（本仓库即默认）；对副本演练时显式指定
- `-HeaderFile` 省略时用内置默认头；`-FixPattern` 默认 `fix_upgradesql`（文件名 `-match`，大小写不敏感）
- 四个镜像参数默认值即上表阿里云引用（**直接拉固定版本**）：`-PgImage` / `-ComparerImage` / `-DbdiffImage`（默认
  `…/plm/dbdiff:3.0.0-rc.12`，**不要随手改成 `latest`**）/ `-BinfmtImage`（用于提示及平台不匹配时的修复命令）
- 驱动给所有 `docker run` 都加了 `--add-host host.docker.internal:host-gateway`：Docker Desktop 下无害，而 engine 为远程/WSL
  端点（`docker context` 非 default）时容器内才能解析到宿主。**若 engine 是远程机而 PG 在本机，则容器根本无法访问本机 PG**
  ——此时要先把 `-PgBase` 指向同机可访问的 PG
- 需要 pwsh 7（harness 默认）；全程约 10-20 分钟（88 个脚本重放 + 双库对比 + 验证轮），放后台跑
- 工作库名 `upmerge_<tag>_base/seq/ver`，成功自动 drop，失败或 `-Keep` 保留

## 产物结构（自顶向下，顺序即执行顺序）

| # | 片段                            | 来源                                                         | 约束                                                                                                                     |
|---|---------------------------------|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| 1 | **固定头** `00_header.sql`      | 内置 `$HeaderSqlDefault` 或 `-HeaderFile` 指定文件           | **逐字固定**，置于产物最前，不参与语义合并                                                                               |
| 2 | **manifest**                    | 自动生成                                                     | 来源清单 + sha256；fix 脚本标注 `[不合并, 末尾原样附加]`                                                                 |
| 3 | **合并正文** `gen_up.sql`       | dbdiff 双库对比 UP 段                                        | 语义合并结果（含 fix 脚本的净效果，因为 fix 脚本也在 seq 真值库中执行）                                                  |
| 4 | **验证补丁**                    | 收敛循环 + STEP 6.5 同步                                     | 自包含生效补丁（`Apply-Patch` 自动带视图依赖 prepass）                                                                   |
| 5 | **fix 脚本** `fix_verbatim.sql` | 文件名匹配 `-FixPattern`（默认 `fix_upgradesql`）的 0.x 脚本 | **按原文件逐字保留**（含文件头注释），追加在产物**最后**；每个文件有 `-- ==== BEGIN/END (verbatim) <filename> ====` 包裹 |

（完整顺序：
`00_header → manifest → 05a_prepass_drop → gen_up → 05b_prepass_recreate → 各轮补丁 → 06.5 同步 → fix_verbatim`）

- 固定头与末尾 fix 片段都是「宿主侧拼装」的内容，**不可**由 dbdiff 生成，所以不走语义合并
- 末尾 fix 片段到达「最后」的方式：合并正文 → 收敛补丁 → 同步补丁 → fix 脚本；ver 库的执行顺序与产物顺序**完全一致**（STEP
  6.8 在同步补丁之后执行 fix 脚本）

## 索引 DDL 兼容规范（硬性）

产物里**所有索引**一律采用“先删后建”的兼容写法，由驱动自动归一化（`Repair-IndexDdl`）：

```sql
DROP INDEX IF EXISTS idx_x;
CREATE INDEX idx_x ON public.t (...);
```

| 禁止的写法                   | 后果                                                                              |
|------------------------------|-----------------------------------------------------------------------------------|
| `CREATE INDEX IF NOT EXISTS` | 目标库已有同名索引（定义可能不同）时**静默跳过**，产物终态与验证态不一致          |
| 裸 `CREATE INDEX`（无 DROP） | 已存在直接报 `relation already exists`，不可重复执行、不可用于已部分执行过的环境  |
| `CREATE INDEX CONCURRENTLY`  | 不能出现在事务内，Go `migrate` 整文件执行必失败；产物是单脚本，无并发建索引的必要 |

- 唯一索引同理：`DROP INDEX IF EXISTS` + `CREATE UNIQUE INDEX`
- **例外：约束背书的唯一索引**（PK / UNIQUE CONSTRAINT）不能用 `DROP INDEX`（会报“被约束依赖”），用
  `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT`（verifier 生成的补丁就是这个形态，驱动不会误伤）
- 目标目录的**源脚本**若出现 `IF NOT EXISTS` / `CONCURRENTLY`，STEP 0 会告警列出文件并提示改写法；产物侧仍会自动归一化，不阻断合并
- 覆盖范围：合并正文（STEP 4）+ 各轮收敛补丁（`Apply-Patch`）；末尾 fix 片段按约定逐字保留、**不**改写

## 流水线（脚本自动执行）

1. **base 库**：空库经 Go migrate 重放到 from 终态 → pg_dump 快照
2. **seq 库**：快照恢复 + migrate 继续执行目标目录**全部** 0.x（含 fix 脚本，= 逐脚本执行真值库）
3. **生成**：dbdiff 双库对比（**server1=seq 基准、server2=base**——dbdiff 3.0.0-rc.12 实测方向与 README 相反，server2 对齐到
   server1）→ 截取 UP 段（到 `-- ===... DOWN` 标记为止）
4. **组装 r0**：固定头 + manifest 头（来源清单+sha256）→ `merged_r0.sql`
5. **ver 库**：快照恢复 + 执行 `merged_r0.sql`
6. **收敛循环 ≤3 轮**：postgrelcomparer(ver vs seq, compare-content both, 排除 `public.schema_migrations`/
   `public.flyway_schema_history`) → 差异作为补丁回灌 ver 库。补丁含 `ALTER COLUMN TYPE` 时自动 prepass：先 DROP
   依赖视图（pg_depend 递归闭包），补丁后从 seq 库 `pg_views.definition` 重建视图及注释（注意 `pg_description` 主键列是
   `objoid`）。**自包含生效补丁**（drop+补丁+重建）才拼入产物
7. **盲区同步**：postgrelcomparer 只比基表、dbdiff 不生成 COMMENT——把 seq 真值库的视图定义与视图注释同步到 ver（比对
   `pg_views`/`pg_description`），同步补丁同样进产物
8. **末尾 fix 附加（STEP 6.8）**：把文件名含 `fix_upgradesql` 的 0.x 脚本按原文件逐字写入 `fix_verbatim.sql` → 在 ver
   库执行 → **立即复跑一次 postgrelcomparer**：fix 的净效果已在 seq 中，故必须零差异；非零差异 = 脚本非幂等 → FAIL（证据
   `incremental.sql`）。此片段**追加在产物最末**
9. **终审**：`pg_dump --schema-only` 归一化（剥 `--`、`\restrict`/`\unrestrict` 行、空行）后 MD5 比对——这是最终门禁，不可省
10. **落仓**：产物写入目标版本目录（按
    `00_header → manifest → 05a_prepass_drop → gen_up → 05b_prepass_recreate → 补丁 → fix` 顺序拼接，UTF-8 no BOM
    逐字落盘）；0.x 脚本（含 fix 脚本）移入 `flyway/migration_archive/<folder>/`（`-NoArchive` 跳过）；证据存
    `backend/upgrade/.merge-work/<to>-<时间戳>/`（自 gitignore）

### 回放与可行性处理（驱动自动完成）

| 现象                                                                                                     | 原因                                                                                                                                                                                                                                                                                                                                                              | 驱动处理                                                                                                                                                                                                                                                                              |
|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CREATE INDEX CONCURRENTLY cannot run inside a transaction block`                                        | Go `migrate` 把整文件当一条 multi-statement 简单查询 → PG 隐式事务。而产品侧 Flyway 7 `PostgreSQLParser.detectCanExecuteInTransaction()` 明确把 `CREATE INDEX CONCURRENTLY`/`REINDEX`/`VACUUM`/`ALTER SYSTEM`/`CREATE\|DROP DATABASE\|TABLESPACE\|SUBSCRIPTION` 排除在事务外（已反编译确认）                                                                      | STEP 3 先试 migrate，失败则**重建 seq 后改用 psql 逐文件自动提交回放**（语义同 Flyway，`03_seq_replay_psql.log`；不写 flyway 历史表，该表所有对比都排除），manifest 记录 `seq 回放: migrate\|psql-sequential`                                                                         |
| `ERROR 0A000 cannot alter type of a column used by a view or rule`（ver 执行初版产物时）                 | dbdiff 给的是**净差异**：原脚本里 `DROP VIEW → ALTER COLUMN TYPE → CREATE VIEW` 的中间步骤被消掉，base 态视图直接挡住 ALTER                                                                                                                                                                                                                                       | STEP 5 正文前计算 base 态视图依赖闭包（`pg_depend` 递归），插入 `05a_prepass_drop.sql`（降序 DROP），正文后插入 `05b_prepass_recreate.sql`（定义取自 seq）。两段都进产物。补丁侧同一机制见 `Apply-Patch`                                                                              |
| `ALTER COLUMN TYPE` 没被 prepass 识别                                                                    | dbdiff 输出的表名**不带 schema 限定**（`ALTER TABLE "t" ...`），旧正则只认 `"public"."t"`                                                                                                                                                                                                                                                                         | `Get-AlteredTables` 正则改为 `(?:"public"\.)?` 兼容两种写法（`Apply-Patch` 与 STEP 5 共用）                                                                                                                                                                                           |
| `pg_dump` 终审报列序差异（`07_schema_diff.txt` 里同一列出现在不同行号）                                  | dbdiff 把原脚本的 **`RENAME COLUMN`** 表达成 `DROP COLUMN + ADD COLUMN`；而 PG 的 `ADD COLUMN` **不复用**被删列的 attnum 槽位，于是合并产物的 attnum/列序与逐脚本终态不一致（v3.8 实证：`V3.8.0.17` 的 `RENAME COLUMN is_sequential → execute_strictly_in_order` 保留 attnum 187，ver 里变成新增列 @207）。postgrelcomparer 不比列序，**只有 pg_dump 终审能发现** | STEP 4 的 `Repair-RenamedColumns`：同表内若「seq 新增列 attnum == base 被删列 attnum」则判为 RENAME，把 ADD/DROP 两行替换为 `RENAME COLUMN` + 必要属性调整（类型/默认值/NOT NULL 均取自 seq 真值库），日志 `列重命名还原: …`                                                          |
| 索引 DDL 不容错（`IF NOT EXISTS` 静默跳过 / 裸 `CREATE INDEX` 重复执行报错 / `CONCURRENTLY` 进不了事务） | 产物要能对“已部分执行过”的环境重放，且 Go migrate 在事务内整文件执行                                                                                                                                                                                                                                                                                              | STEP 4 与 `Apply-Patch` 调 `Repair-IndexDdl`：每个 `CREATE [UNIQUE] INDEX` 改写为 `DROP INDEX IF EXISTS <name>;` + `CREATE [UNIQUE] INDEX <name> ...;`（去 CONCURRENTLY / IF NOT EXISTS）；STEP 0 对源脚本告警                                                                        |
| 收敛循环卡在同样几条差异（补丁回灌无效）                                                                 | 表达式的 **deparse 往返差异**：如 varchar 列 `CHECK ("c" IN ('a','b'))` 反解为 `ANY ((ARRAY[...])::text[])`，该文本重解析后变成逐元素 `::text` 形态，verifier 的补丁永远无法把 ver 变成 seq 的文本                                                                                                                                                                | 驱动检测到「本轮语句与上轮完全相同」会提示处置：**用 fix_upgradesql 脚本按原始脚本表达式文本重建该对象**（原样附加到产物末尾），即本 skill 的破局手段                                                                                                                                 |
| psql 失败看不到任何 stderr                                                                               | 本环境下直接 `docker run psql`（作为容器 PID1）时 stderr 会丢失（实测）；`sh -c` 内部重定向正常                                                                                                                                                                                                                                                                   | `Invoke-SqlFile` 统一用 `sh -c` 把 stderr 重定向到挂载目录，失败时在宿主侧回显尾部并带 `_psql_*.err` 路径                                                                                                                                                                             |
| `host.docker.internal` 无法解析 / `invalid mode: /workmnt`                                               | engine 不是 Docker Desktop（`docker context` 非 default，如远程 WSL 端点）：不自动解析该域名，也不接受 Windows 路径作为 `-v` 源                                                                                                                                                                                                                                   | 所有 `docker run` 加 `--add-host host.docker.internal:host-gateway`；挂载路径启动时探测，必要时自动翻译为 `/mnt/<drive>/...`                                                                                                                                                          |
| `postgrelcomparer: exec format error`                                                                    | `…/plm/postgrelcomparer:distroless` **只有 arm64** 构建                                                                                                                                                                                                                                                                                                           | 驱动预检会探活并在开工前报错，给出修复命令：`docker pull registry.cn-hangzhou.aliyuncs.com/plm/binfmt:latest` → `docker tag` 成 `tonistiigi/binfmt:latest` → `docker run --privileged --rm tonistiigi/binfmt:latest --install arm64`（实测可用，比原生慢）；彻底解决需自建 amd64 镜像 |

## 产物处理（agent 向用户交付时）

- PASS：报告产物路径、schema MD5、归档清单、`.merge-work` 报告目录；说明产物结构（固定头 / 合并正文 / 末尾 fix 脚本 N 个）；提醒
  git 提交（Conventional Commits + `v3.7.1` 版本号）；提醒**已执行过 0.x 的存量环境按团队约定人工处理**（合并脚本仅保证全新环境的终态一致）
- FAIL：脚本保留工作库与全部证据（02/03/04/06/07 日志）。按步骤定位：STEP 2/3 重放失败看 `0x_*_replay.log`；STEP 5 合并脚本执行失败看
  dbdiff 产物（常见：顺序依赖，检查 UP 段是否完整）；STEP 6 三轮未收敛说明存在 verifier 无法表达的差异（视图/函数/序列）——人工
  diff `07_schema_diff.txt`；**STEP 6.8 报差异 = fix_upgradesql 脚本非幂等**（合并正文已含其净效果，复跑不该再变），看
  `incremental.sql` 与对应脚本，必要时与脚本作者确认，不要改生成 SQL
- **不要手工修改生成的 SQL 来"修复"差异**——先理解差异来源，生成侧（dbdiff
  用法/参数）和验证侧（postgrelcomparer）是相互独立的两个引擎，修验证补丁要经收敛循环闭环

## 已知边界

- **合并产物会丢 `CONCURRENTLY`**：`CREATE INDEX CONCURRENTLY` 经 dbdiff 后变成普通 `CREATE INDEX`（v3.8 产物 0 处
  CONCURRENTLY）。全新环境重放无影响，但把产物直接跑在活跃库上会锁表，需人工评估
- **CHECK/默认值表达式的 deparse 往返差异（v3.8 实证）**：dbdiff 的 CREATE TABLE 会带上 PostgreSQL 反解后的表达式文本；该文本重解析可能得到
  **不同的表达式树**，于是 ver 与 seq 的 `pg_get_constraintdef` 文本永久不同，verifier 的补丁永远收敛不了（补丁把 seq
  的文本原样回灌，重解析后又变回 ver 形态）。典型：varchar 列 `CHECK ("c" IN ('a','b'))` ↔
  `CHECK (((c)::text = ANY ((ARRAY[...]::character varying])::text[])))` ↔
  `CHECK (((c)::text = ANY (ARRAY[('a'::character varying)::text])))`。**两者语义等价但文本不等价**，pg_dump
  终审也会算它们不同。破局：用 `fix_upgradesql` 脚本按**原始脚本的表达式文本**重建该对象（原样附加于产物末尾）——v3.8
  实证：加尾部重建后 postgrelcomparer 立刻 0 差异
- **fix_upgradesql 脚本必须幂等**：它们既在 seq 真值库按原位置执行（净效果因此已进入 dbdiff 合并正文），又在产物末尾原样复跑一次。复跑零差异是
  STEP 6.8 的硬门禁；若脚本依赖「当前状态恰好是执行前状态」则不幂等，流水线会 FAIL——此时该脚本只能人工处理，别期望调参绕过
- **固定头与 seq 的关系**：固定头只在 ver（产物）侧执行，seq 真值库没有它。默认头的 DROP `air_inst_view_ws_*` 若在 seq
  中仍存在这些视图，STEP 6.5 会从 seq 把它们重建回产物尾部（净效果为零）；若目标是「真的删掉这些视图」，需同步从 seq
  侧删除来源（或与用户确认该头的真实语义），否则终审只反映净效果
- **dbdiff 版本钉死 rc.12**：`--server1/--server2` 的方向语义（实测与 README 相反）与撇号转义 bug 均在 rc.12 上实测。换版本（如
  rc.34）必须先用已知差异的双库复验方向，否则收敛循环会把差异“补”成错误产物
- **dbdiff 撇号转义 bug（驱动已自动修复）**：值内撇号被转义成 `\'`（MySQL 风格），在 `standard_conforming_strings=on`
  下会提前终结字符串导致后续解析错位（2026-09-24 v3.7 合并实证，错误可能延迟数百行才爆）。驱动在 UP 段落仓前自动归一化为
  `''`；`\"`/`\\` 属数据级差异，由收敛循环自愈
- 视图/函数/序列的**内容**由 pg_dump 终审兜底（dbdiff 对 COMMENT/列类型/元数据有已知盲区，PoC 已实证并被验证补丁闭环兜住）
- 无主键表的数据对比依赖 ctid 策略，verifier 输出仅注释说明；fix 脚本对无主键表的数据清洗因此主要靠「末尾原样复跑 +
  幂等」保证，而非 verifier
- 生产环境 PG15 与本机 PG16.10 存在小版本差，首次上线前建议用 PG15 容器演练一次
- 本机 PoC/演练产物在 `D:\AppData\code_airedge\poc_merge\`（PoC报告.md 为方法论文档）
