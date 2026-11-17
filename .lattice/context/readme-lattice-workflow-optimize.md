---
feature: readme-lattice-workflow-optimize
requirement_doc: null
created: 2026-07-14
---

# readme-lattice-workflow-optimize

> 优化根 README 与 PM 协作说明：Lattice 改为 submodule + 安装脚本（安装目录检入）、`pm-git-branch` 改名为 `pm-git`、需求文档预览改为浏览器 + 插件轻量方案；自动化测试说明待 TESTAPI 调研后再细化。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-14 | Context 锚点命名为 `readme-lattice-workflow-optimize`，无独立 requirement doc | 目标来自对话/README 改进，无单独 feature 规范 | 拆成多份 context（过碎） |
| 2026-07-14 | Lattice：git submodule + 手动安装脚本；omp/pi **不**直接扫描 submodule 源码树，改引用安装产物目录；该目录**检入 Git** | 团队使用相同 Lattice 版本；符合「不引用源代码、直接安装技能」；随代码更新走 | B：继续直接扫描 submodule skills（与「安装技能」目标不符）；安装目录不检入（每人/CI 必跑 install，版本易漂移） |
| 2026-07-14 | 产品文档预览：推荐浏览器安装 **Markdown Viewer** + **Mermaid Diagram** 插件，替代以 VS Code 为默认的预览路径 | 无需 VS Code，更轻量，且能正确渲染 MD 与 Mermaid | 继续以 VS Code 为主；仅 GitHub Web 预览（本地离线/未推送文档不便） |
| 2026-07-14 | 从 Level 1（Capabilities）开始渐进设计 | 涉及 README、技能分发、PM 指南、命名多组件 | 跳到 Level 4 合同（范围未对齐） |
| 2026-07-14 | Level 1 Capabilities 批准（含显式改名能力） | 现名仍为 `pm-git-branch`，须改名技能目录、技能标识与 `.sh`，并同步文档引用 | 仅改文档仍写旧名（与目标不符） |
| 2026-07-14 | Level 2 Components 批准；安装产物目录定为 `.omp/skills/`（仅 Lattice 扁平技能；`pm-git` 仍在 `backend/.pi/skills`） | 贴近 omp 习惯；配置扫描单目录；与项目定制技能物理分离 | B：`skills/lattice/` 或 `.lattice/skills-installed/`；把 `pm-git` 也装进 `.omp/skills`（混淆官方 Lattice 与项目技能） |
| 2026-07-14 | Level 3 Interactions 批准；配套脚本改名为 `pm-git.sh` | 与技能名 `pm-git` 一致，消除 pm-branch / pm-git-branch 分裂命名 | B：仅改技能名、保留 `pm-branch.sh` |
| 2026-07-14 | 安装与发现必须同时支持 **omp 与 pi**（不只 omp） | 仓库双工具并存；仅写 `.omp` 会导致 pi 路径滞后 | 只装/只配 omp |
| 2026-07-14 | Level 4：安装产物一份共享（Option A）；`--target all\|omp\|pi`；omp/pi 配置均指向 `.omp/skills` | 避免双份拷贝；双工具共用检入产物 | B：omp/pi 各写一份安装目录 |
| 2026-07-14 | 自动化测试文档：去掉 Postman / gen-postman / fix-postman 等现述；注明待小刚 **TESTAPI** 调研完成后再细化 | 现行 Postman 叙事即将过时，避免 README/工作流继续推广 | 保留 Postman 节点直至 TESTAPI 落地（文档债务） |
| 2026-07-14 | Design approved at Level 4. Blueprint complete ready for implementation. | 四级设计与关键判断均已确认 | — |
| 2026-07-14 | 实现采用 Full autonomy；`lattice/` 转为 submodule 钉 `75b7e07`（原 v2.0.0）；项目说明迁至根 `LATTICE.md`（不再依赖原 `lattice/VENDOR.md`） | 贴合蓝图；VENDOR 随 submodule 覆盖无法长期保留仓库定制说明 | 暂保留 vendored 目录偏离蓝图 |
| 2026-07-14 | Implementation complete against Level 4 contracts | code-forge 落地工具链 + 文档 | — |
| 2026-07-14 | 复查：补全 submodule（此前索引回退导致无 `.gitmodules`）；同步 `backend/README.md`、slash-commands、AGENTS Prompt 列表的 TESTAPI 表述 | 转换未提交曾被工作区状态冲掉；旁路文档遗漏 | — |
| 2026-07-15 | QA 文档补充：可能引入技能读取 `requirement-forge` 输出、按标准生成测试用例供 QA 使用；**待调研确定**后落地 | 与 TESTAPI（接口自动化）解耦，先占位对齐协作叙事 | 立刻定技能名/命令（时机过早） |

## Open Questions

- TESTAPI 调研结论落地后，需另开特性补全 README / QA 指南中的自动化测试章节（本蓝图只留占位说明）。
- 是否提供「消费 `requirement-forge` feature / AC → 按标准生成用例草稿」的 QA 技能：待调研确定后再选型、命名与写入推荐命令。

## Constraints

- Lattice 消费方式：安装产物目录检入；配置指向安装目录，不直接引用 submodule 内 skills 源码路径。
- 安装目录须保证团队 clone 后即可发现同一套 Lattice 技能（无需每人再装一遍才能对齐版本号；升级仍靠 submodule bump + 重跑 install + commit 产物）。
- 产品预览默认路径不以 VS Code 为前置依赖。
- 本特性须完成 `pm-git-branch` → `pm-git` 的技能与脚本改名（不仅文档措辞）。
- Lattice 扁平安装产物目录固定为 `.omp/skills/`；项目定制技能（含 `pm-git`）不放入该目录。
- PM 分支技能标识与脚本文件名均为 `pm-git` / `pm-git.sh`（不再使用 `pm-git-branch` / `pm-branch.sh`）。
- Lattice 项目内安装与 Agent 发现配置须同时覆盖 **omp 与 pi**；禁止只更新其中一侧。
- 本特性触及的工作流文档中，**不得**再以 Postman / gen-postman / fix-postman 作为推荐自动化路径；自动化测试细节待 TESTAPI 调研后再写。

## Key Files

| Path | Role | Layer |
|------|------|-------|
| `.gitmodules` + `lattice/` | Lattice submodule（钉 v2.0.0 / 75b7e07） | 技能供给 |
| `LATTICE.md` | 升级 / 安装说明（替代原 VENDOR） | 文档 |
| `scripts/install-lattice-project-skills.sh` | 安装到 `.omp/skills`；`--target all\|omp\|pi` | 工具链 |
| `.omp/skills/` | 检入的 27 个扁平技能 | 技能供给 |
| `.omp/config.yml`、`.pi/settings.json`；backend/web 对应配置 | 发现路径 → `.omp/skills` | 配置 |
| `backend/.pi/skills/pm-git/` | `pm-git` + `pm-git.sh` | 项目技能 |
| `README.md` | 工作流、Agent、TESTAPI 占位 | 文档 |
| `docs/workflows/pm-guideline.md` | 分支入口 + 浏览器预览 | 文档 |
| `docs/workflows/qa-guideline.md` | TESTAPI 待定 | 文档 |
| `.devcontainer/setup.sh` | 容器内校验 `.omp/skills` | 工具链 |
| `AGENTS.md`、`backend/AGENTS.md`、`backend/.pi/README.md` 等 | 旁路同步 | 文档 |

## Design: Level 1 -- Capabilities

（略；见设计阶段批准内容）

## Design: Level 2 -- Components

（略；见设计阶段批准内容）

## Design: Level 3 -- Interactions

（略；见设计阶段批准内容）

## Design: Level 4 -- Contracts

（略；见设计阶段批准内容）

## Design Summary

- **Design status: Approved -- ready for implementation**
- **Implementation status: Complete 2026-07-14**

## Design Checkpoint

- Current: Implementation complete
- Level 1–4: Approved 2026-07-14
- Impl: Full autonomy landed 2026-07-14
