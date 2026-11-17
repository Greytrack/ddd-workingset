# Lattice Telemetry Mechanism (Hook-Driven Architecture)

> 收集端 Hook 驱动的交互事实记录机制。

## Overview

Lattice 技能埋点机制：
- **零技能修改**：不需要在任何 `SKILL.md` 中编写埋点指令。
- **确定性触发**：通过各 Agent 平台的 Stop 类 Hook 自动从转录日志提取用户输入与 AI 最终回复；当前注册 omp / pi / codex / cursor / claude-code / zcode / reasonix 七个 Hook 运行时，外加 dsh / reasonix（经会话落地收集覆盖，见下文；reasonix 为 hook 实时链路 + 收集器兜底双路径，marker 去重防双路上报）。各平台配置均随仓库分发，**同事拉代码即可生效**；子目录（`web/`、`backend/`）启动会话由对应目录内的注册文件覆盖。
- **本地暂存**：以 `RawEvent` 事实数据追加到 `${repo_root}/.lattice/telemetry/cache/events.ndjson`（容量上限 10000 条，超限丢弃最旧）。
- **自动上报**：两条触发路径幂等并存——① Stop 钩子在记录事件后按 10 分钟节流派发**后台 flush**（detached 进程，不阻塞会话），不依赖任何手工配置；② `git push` 时由 **pre-push** 钩子同步触发 flush（git 先与远端建立连接并确认有 refs 可推才运行 pre-push，天然满足"确认在线才上传"；git 无原生 post-push，`post-push` 文件仅为手动/包装脚本兼容保留）。`api_url` 未配置时 flush 静默跳过、事件保留；上传失败时暂存文件（`events.ndjson.flushing-*`）保留待下次重试。

---

## 核心配置与文件

| 文件 | 作用 |
|------|------|
| `telemetry.config.json` | 仓库级开关与 `api_url`。按序探测：仓库根 `telemetry.config.json` → `.lattice/telemetry.config.json` → `telemetry.config` / `.lattice/telemetry.config` → `telemetry.json` / `.lattice/telemetry.json` → `telemetry.enabled` / `.lattice/telemetry.enabled`（首个存在者生效，空文件视为开启）；`TELEMETRY_ENABLED` / `TELEMETRY_API_URL` 环境变量优先于文件；`api_url` 缺省时回退读 `.lattice/telemetry/api.url`。本仓库默认 `{"enabled": false}`。所有探测基于 `git rev-parse --show-toplevel` 解析的仓库根，任意子目录下均正确 |
| `.claude/settings.json` | Claude Code 的 Stop Hook（每轮回复后调用 `node telemetry-emitter.mjs hook`）。命令由 `telemetry-hook-command.mjs` 统一渲染（跨平台 bootstrap，见下节），子目录启动由 bootstrap 的 cwd 上溯解析。omp 实测**不会**加载此文件的 Claude 格式 hooks，omp 需走下方扩展 |
| `.lattice/telemetry/telemetry-hook-command.mjs` | 各平台 Hook 注册命令的唯一事实来源：渲染 `node -e "<bootstrap>" "<agentTool>" "<scriptName>" ["${平台项目目录模板}"]` 形式的命令串。bootstrap 从 cwd 逐级上溯定位 `.lattice/telemetry`，等价 git toplevel 的就近语义但零 shell 依赖；命令串不含 `$` `` ` `` `\` `%` `!` `"`，在 macOS/Linux sh、Windows cmd、PowerShell、Git Bash 下均为纯字面量。`AGENT_TOOL` 由 bootstrap 写入子进程环境（替代 POSIX 的 `VAR=val` 前缀写法）。单测强制校验 shell 字符安全 + 13 份配置与本模块逐字节同步（claude/cursor/zcode/reasonix 各 3 份 + codex 1 份） |
| `.lattice/telemetry/telemetry-hook.mjs` | pi / omp 的共享非阻塞上报逻辑：从当前目录解析 Git 根目录后调用 emitter |
| `.omp/extensions/telemetry-stop.ts`、`.pi/extensions/telemetry-stop.ts` | omp / pi 项目级扩展：注册 `session_stop` / `agent_settled` 事件并委托共享逻辑。**这些平台不做祖先 walk-up**，故 `web/`、`backend/` 各有同名副本（import 路径多一级 `../`）；pi 项目扩展首次使用需信任（交互提示或 `pi --approve`） |
| `.codex/hooks.json` | Codex 的 `Stop` hook（跨平台 bootstrap 命令，见 `.lattice/telemetry/telemetry-hook-command.mjs`）；命令变更后需在 Codex `/hooks` 重新信任一次 |
| `.cursor/hooks.json` | Cursor 的 `stop` hook（跨平台 bootstrap 命令）；transcript 缺失时回退读 `CURSOR_TRANSCRIPT_PATH` 环境变量。根目录与 `web/`、`backend/` 各一份（子目录副本合并了该目录既有的其他钩子） |
| `.zcode/config.json` | ZCode 的 workspace 级 Hook 配置（`hooks.enabled` **必须为 true**，配置文件钩子默认禁用）：`UserPromptSubmit` 暂存用户输入 + `Stop` 配对上报，命令经 `telemetry-zcode-hook.mjs`。**用 `type: "process"`（argv 直派、不经 shell）**：Windows 上避开 `cmd.exe /d /s /c` 这一层，GUI 场景不再闪现控制台窗口（zcode 官方推荐的可移植形态）；`args` 同样做 `${ZCODE_PROJECT_DIR}` 模板展开，超时字段为 `timeoutMs`。根目录与 `web/`、`backend/` 各一份，适配 zcode 的 workspace 发现规则（按 discoveryOrder 解析单个配置，不会重复触发） |
| `.lattice/telemetry/telemetry-zcode-hook.mjs` | ZCode 适配器：zcode Stop 分发的临时转录只含单行 assistant 文本（`{"message":{…}}` 无 `type` 字段，emitter 解析器不识别）且无用户输入，故在 `UserPromptSubmit` 时将 prompt 暂存到 `cache/pending/`（按会话哈希隔离，24h 过期清理），`Stop` 时配对合成 Claude 格式转录交给 emitter，emitter 本身零改动。入口判定用 realpath 归一化 + Windows 大小写折叠（argv[1] 与 import.meta.url 在 Windows 可能盘符/大小写不一致，直接字符串比较会静默失效） |
| `.lattice/telemetry/dsh-session-collector.mjs` | dsh（DeepSeek Harness）会话落地收集器：dsh 全模式（headless 一次性 / web / tui）都会把会话持久化到 `~/.dsh/sessions/<项目>/<会话>/session*.jsonl.zstd`（多帧 zstd 的 JSONL；`compression: 'none'` 时为明文 JSONL；头部含 cwd，事件含 `user/message`[排除注入上下文]/`assistant/message`/`turn/end`）。flush 时增量扫描（按 seq 水位去重，只回看 24h 内有更新的会话），配对轮次后按会话 cwd 的 git 根归属暂存 —— **不依赖任何 dsh 侧配置**（headless 不加载 patch/preset、host 平面插件收不到会话事件，进程内接入面已验证不可行）。zstd 能力检查为逐文件粒度：Node ≥22.15 解压 `.zstd` 会话；Node 18/20 无内置 zstd 时仅跳过 `.zstd` 文件（水位不推进，升级后补收），明文会话照常收集。依赖经 flush 参数注入（反向静态 import 会因 emitter 顶层 `await main()` 形成 ESM 循环死锁） |
| `.reasonix/settings.json` | Reasonix 的项目级 Hook 注册（原生 hooks 机制：`UserPromptSubmit`/`Stop` 事件，命令经平台 shell `sh -c`/`cmd /c` 执行，stdin 喂一行 JSON 载荷）：`UserPromptSubmit`（阻塞型，`timeout: 5000`）暂存用户输入 + `Stop`（`timeout: 10000`）配对上报，命令由 `telemetry-hook-command.mjs` 渲染（无 projectDirHint——hook 默认以会话 cwd 执行，bootstrap 的 cwd 上溯已覆盖子目录工作区）。根目录与 `web/`、`backend/` 各一份。**项目 hooks 必须信任后加载**（桌面端"设置 → Hooks → 信任此工作区"，CLI `/hooks trust`；信任写在用户 `~/.reasonix/trust.json`，不会随仓库自动生效）；配置变更需重启 reasonix 会话才生效 |
| `.lattice/telemetry/telemetry-reasonix-hook.mjs` | Reasonix Hook 适配器（与 zcode 适配器同构）：Stop 载荷只有 `lastAssistantText` 无 transcript、无 `session_id`，故在 `UserPromptSubmit` 时将 prompt 暂存到 `cache/pending/`（按 `cwd + turn` 哈希隔离，24h 过期），`Stop` 时配对（**两者必须齐备**——hooks 中途启用导致缺暂存时放弃该轮，不用占位文本伪造 user 输入）合成 Claude 格式转录交给 emitter。同工作区并发同轮次会话的暂存碰撞可忽略（载荷无 session_id，接受该边界）。每个事件都会触碰 hook 活跃 marker（见收集器行） |
| `.lattice/telemetry/reasonix-session-collector.mjs` | reasonix 会话落地收集器（flush 时兜底），四种存储互为兜底：① **新版 v4**（旧版桌面，Windows）：`%APPDATA%\reasonix\projects\<路径改写>\sessions-v4\<hash>\events.frames`，RX4F 帧头 + zstd 分帧压缩（需 Node ≥22.15），解压后为无换行的连续 JSON 记录（batch/begin|event|end），事件 payload 为 base64 的 OpenAI 消息；真实用户输入带 `raw_content`（host 注入的 `<session-context>`/`<workspace>` 无此字段，予以排除）；时间戳取该轮最后一条 assistant 消息所在 commit（batch/begin）的 createdAt（提交级精度）。② **Reasonix STUDIO 项目会话**：`%APPDATA%\reasonix\projects\<路径改写>\sessions\<session>.jsonl`；同名 `.ckpt/turn-N.json` 的 `prompt` 是 host 剥离后的真实用户输入，`msgIndex` 指向 jsonl 中该轮 user 消息，收集器按 ckpt 切轮并从 `(msgIndex, 下一轮 msgIndex)` 区间取最后一条 assistant 文本（无 ckpt 的旧会话才回退到 jsonl 注入块剥离）；同名 `.jsonl.meta` 提供 `model`/`updated_at`/`turns`/`in_flight_turn`（末轮进行中标记），同名 `.events.jsonl` 提供回退路径的每轮精确时间；带 `imported_from` 的 `v4-*.jsonl` 是 v4 迁移副本，跳过防重复。③ **Reasonix STUDIO 桌面会话**：`%APPDATA%\reasonix\desktop-sessions-v5\by-id\<id>\events.frames`，与 v4 相同的线性帧格式，归属取同目录 `header.json` 的 `cwd`；若同一 id 已存在于某项目 `sessions-v4` 则跳过（防双通道重复）。④ **旧版明文**（legacy 兜底）：`~/.reasonix/sessions/code-<项目>[-<时间戳>].jsonl`，时间戳从同名 `.events.jsonl` 取。归属（方案 A 分仓库）：v4/STUDIO 项目会话用 `%APPDATA%\reasonix\desktop-projects.json` 的 `projects[].root` 做权威注册表（项目目录名 = root 把 `:`/`\` 换成 `-`）；STUDIO 桌面会话用 `header.json.cwd`；旧格式用文件名 token。仅收集 git 根 == 当前仓库的会话；只推进已完结轮次的水位（v4/桌面按 seq、STUDIO 项目会话与旧格式按轮次序号），进行中的轮次下次再收，避免丢轮。**与 hook 实时链路的双路去重**：hook 适配器每事件触碰 `cache/reasonix-hook-active.json` marker，marker 新鲜（24h 窗口）时收集器**只推进水位、不追加事件**（活跃期轮次由 hook 实时上报）；marker 停更（信任撤销/hooks 失效）后自动接管，且水位已跟到头部，不会把 hook 期轮次回填一遍。注意：本收集器的 flush 接入段曾在合并冲突解决时被意外丢弃（f25ff1c4a3 引入、62c731795d 丢失致收集器成死代码），已恢复——**改动 flush 前段时务必保留 dsh/reasonix 两个收集块** |
| `.lattice/telemetry/cache/events.ndjson` | 本地未上报事件 Staging 存储（`cache/` 整体 gitignore） |
| `.githooks/pre-push` | Git pre-push 钩子（`git push` 过程中触发 flush；需 `git config core.hooksPath .githooks`，未配置者由 Stop 钩子搭载的节流 flush 兜底） |

---

## 跨平台与 Node 兼容性

**目标环境**：macOS / Linux / Windows × Node ≥18（`node --test .lattice/telemetry/*.test.mjs` 全绿；zcode/claude-code/codex/cursor 四类 Stop 链路经注册命令串端到端真机模拟）。

### Hook 命令为何不用 `AGENT_TOOL=x node "$(git rev-parse --show-toplevel)/..."`

该写法依赖两项 POSIX shell 专属语法，Hook 平台在 Windows 上经 cmd.exe / PowerShell 执行命令串，两者均不支持：`VAR=value command` 前缀会被当作命令名报错；`$(...)` 不被展开、原样进入 node 的模块路径。因此所有平台统一改用 `telemetry-hook-command.mjs` 渲染的 **shell 中性命令**（`node -e "<bootstrap>" ...`），设计要点：

1. **bootstrap 内联脚本**（Node 内完成，零 shell 依赖）：从 cwd 逐级上溯查找 `.lattice/telemetry`（与 git toplevel 就近语义一致，子目录启动天然可用）；把 agent 标识写入子进程 `AGENT_TOOL` 环境变量；`stdio:[0]` 把平台喂入的 stdin 载荷直接透传给子脚本。
2. **字符安全约束**：bootstrap 不含 `$`（sh/PowerShell 展开）、反引号（sh/PowerShell 替换）、`\`（sh 转义）、`%`（cmd 变量展开）、`!`（cmd 延迟展开）、`"`（引号定界），故同一命令串在 sh / cmd / PowerShell / Git Bash 下都是纯字面量；单测强制校验。
3. **项目目录模板兜底**：claude/zcode 命令末位带 `${CLAUDE_PROJECT_DIR}` / `${ZCODE_PROJECT_DIR}`——平台做模板展开或 POSIX 环境展开时直击仓库根；未展开时残留字面量被 existsSync 安全忽略，回落 cwd 上溯。
4. **子进程派发的可执行文件选择**：bootstrap 自身由 `node -e` 执行，其内部 `spawnSync(process.execPath, …)` 必为真 node；而 **omp/pi 扩展适配层（telemetry-hook.mjs）不得用 `process.execPath`** —— 宿主是 Bun 系应用（omp）时它指向 omp 自身，用它 spawn 脚本会再起一个完整 omp 实例（加载项目扩展后在其生命周期内递归 spawn，实测 10 分钟衍生三代、每实例数百 MB RSS）。适配层经 `resolveNodeExecutable()`：execPath 是 node（含 `.exe`）时直接用，否则回落 PATH 上的 `node`；找不到时 spawn 的 error 事件被静默吞掉（遥测缺失可接受，递归 fork 不可接受）。后台 flush 加 `windowsHide: true` 防止闪现控制台窗口。
5. **zcode 走 `type: "process"`**：Windows 上 command 型经 `cmd.exe /d /s /c` 执行，cmd 这一层在 GUI 场景可能闪现黑色控制台窗口；process 型由 zcode 直接 CreateProcess node.exe（仅 `.cmd/.bat` 可执行文件才包 cmd），链路中完全没有 cmd。process 型与 command 型同样从 stdin 收 JSON 载荷、args 同样做模板变量展开。

### 其余兼容性要点

- **载荷投递与 stdin 读取**（emitter `hook()` 与 zcode 适配器）：omp / pi 扩展适配层经 `TELEMETRY_PAYLOAD` 环境变量投递载荷 —— Bun 系宿主对 spawn 子进程的 stdin `end()` 不保证送达 EOF，阻塞读 fd 0 会永久挂起（实测孤儿进程挂 18 分钟以上）；env 无 EOF 语义，emitter 侧 env 优先。其余平台仍走 stdin：优先 `readFileSync(0)` 同步读；macOS GUI 父进程 / Windows 非阻塞管道抛 EAGAIN 时回退异步流读取（流内部自行处理 EAGAIN）。
- **dsh 收集的 zstd 门槛**：`zlib.zstdDecompressSync` 需 Node ≥22.15；能力检查为逐文件粒度，Node 18/20 上明文会话照常收集、`.zstd` 会话跳过且水位不推进（升级 Node 后自动补收）。
- **git 子进程**：`execFileSync('git', …)` 在 Windows 由 CreateProcess 自动补 `.exe` 并搜索 PATH，无需 shell。**所有 `execFileSync` / `spawn` 调用（emitter 的仓库根解析、user/remote/branch、requirements HEAD，各 hook 适配器，dsh/reasonix 收集器）必须带 `windowsHide: true`**：Codex 等平台以 `CREATE_NO_WINDOW` 启动 hook 外壳，进程本身没有控制台；此时再派生 git（控制台程序）且不加该标志，Windows 会为它新分配一个控制台窗口，表现为每轮上报都闪现一次黑窗。
- **omp / pi 扩展**（`.omp`/`.pi`/`web`/`backend` 的 `telemetry-stop.ts` → `telemetry-hook.mjs`）：进程内 Node API，无 shell 参与；经 `TELEMETRY_PAYLOAD` 环境变量投递载荷、`resolveNodeExecutable()` 解析真实 node 派发 + `windowsHide`；扩展入口带嵌套防护 —— argv 入口指向 `telemetry-emitter.mjs`（嵌套 omp 实例）时跳过注册，切断递归 fork 链。
- **pre-push**：git 钩子在 Windows 由 Git for Windows 自带的 sh 执行，POSIX 语法天然可用，无需改造。

---

## CLI 命令速查

```bash
# 1. 检查当前仓库是否已启用埋点
node .lattice/telemetry/telemetry-emitter.mjs is-enabled

# 2. 查看当前埋点状态（暂存事件数、API 端点等）
node .lattice/telemetry/telemetry-emitter.mjs status

# 3. 手动触发批量上传（通常由 Stop 钩子节流搭载或 pre-push 钩子自动调用；含 dsh / reasonix 会话落地收集）
node .lattice/telemetry/telemetry-emitter.mjs flush
```

---

## RawEvent 事实字段（收集端发送）

| 字段 | 类型 | 说明 |
|------|------|------|
| `event_id` | string | UUID v4，全局唯一去重键 |
| `user_input_text` | string | 当前轮次用户输入原文 |
| `ai_response_text` | string | 当前轮次 AI 最终回复原文（不含 thinking/中间工具调用） |
| `generate_time` | number | 事件生成毫秒时间戳（`Date.now()`） |
| `user` | string | 开发者 Git 用户名（`git config user.name`） |
| `repo_remote_url` | string | 仓库远程地址（`git remote get-url origin`） |
| `branch_name` | string | 当前分支名（`git rev-parse --abbrev-ref HEAD`） |
| `agent_tool` | string | Agent 工具标识：`omp`（默认）/ `pi` / `cursor` / `claude-code` / `codex` / `zcode` / `dsh` / `reasonix` / `agy`，按环境变量探测（注意 zcode 会同时注入 `CLAUDE_*` 兼容变量，探测顺序在 claude-code 之前）；reasonix 走 hook 链路时由 bootstrap 注入 `AGENT_TOOL=reasonix`、走收集器兜底时由收集器直接标注 |
| `source_session_id` | string \| null | 上游会话 ID 或 transcript 路径的 SHA-256；不上传原值 |
| `ai_provider` | string \| null | AI 模型厂商（可选） |
| `ai_model` | string \| null | AI 模型 ID（可选） |
| `report_time` | number \| null | 上报时服务端填充的时间戳 |
| `requirement_id` / `feature_name` / `trace_id` / `trace_source` | null | 收集端不填；服务端也会忽略客户端伪造值 |

---

## 服务端推导职责（F2 SessionClusterer）

服务端接收到 `RawEvent[]` 与 `requirements_commit`（服务端按该 commit 从需求仓库镜像解析文档快照；旧版 emitter 直传的 `context_snapshot` 仅作兼容）后，由后台异步任务推导以下分析字段：
1. `session_id`：优先按仓库、工具及 `source_session_id` 的服务端哈希生成；旧事件按仓库/工具/30 分钟回退聚类，会话命令继续切分。
2. `round_number`：Session 内按时间顺序递增排序（1, 2, 3...）。
3. `skill_name`：多轮技能状态机推导，从 `/skill:xxx` 持续到技能段结束。
4. `event_type`：标记技能段首条（`skill_started`）、中间轮（`skill_in_progress`）、末条（`skill_completed`）或普通对话（`normal`）。
5. `trace_id` / `requirement_id` / `feature_name`：通过需求仓库快照中的文件路径、完整 REQ ID 或唯一长 Task 号关联；分支名不参与识别。
