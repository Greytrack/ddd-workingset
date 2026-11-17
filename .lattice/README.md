# Lattice 规约区

> Lattice **动态上下文层**：团队标准与协作产物的共享真实来源（应检入 Git）。
> 用词对齐中文译本 [ericwq/MDE …/lattice](https://github.com/ericwq/MDE/tree/main/tools/reduce-friction-ai/lattice)；根 [README.md](../README.md) 有摘要。
> 角色协作**文字指南**见 [docs/workflows/](../docs/workflows/)。

## 目录结构

```
.lattice/
├── config.yaml              # 中心配置文件（根目录下唯一的文件）
├── standards/               # 精炼技能产出的定制化文档
│   ├── architecture.md
│   ├── clean-code.md
│   ├── ddd-principles.md
│   ├── knowledge-base.md
│   ├── language-idioms.md
│   └── review-standards.md
├── requirements/            # 【规划】git submodule → 产品仓（产品单分支持续迭代）
│   ├── index.md
│   ├── epics/
│   └── features/
├── context/                 # 每个功能的动态文档（决策与设计蓝图）— 工程仓维护
├── reviews/                 # 项目健康度的审查日志
│   └── review-log.md
├── learnings/               # 操作性经验（learning-harvest 原子技能管理）
│   └── operational-learnings.md
└── insights/                # architecture-compass 产出的架构见解（可选）

# 本仓库扩展
.lattice/
└── ddd/                     # 限界上下文与领域模型规约
    ├── bounded_contexts.md
    └── contexts/
```

## `requirements/` 与产品仓

| 项 | 约定 |
|----|------|
| 形态 | `.lattice/requirements/` = **git submodule**，指向产品仓库 |
| 产品仓分支 | **单一分支**，产品在其上持续迭代 epic / feature |
| 开发权限 | 对产品仓该分支仅有**只读权限**，不具备编辑 / push 权限（由仓库 ACL 约束，不仅是流程约定） |
| 工程仓 | 父仓只记录 submodule 的 **gitlink**（钉死某次产品 commit）；可更新指针以拉取产品已发布内容 |
| 谁改规格 | **仅产品**角色在产品仓内改（如 `/skill:requirement-forge`）；开发设计产出写在工程仓 `.lattice/context/` |

当前若仍为普通目录，属过渡期；接入 submodule 后以上约定生效。

## 使用方式

| 场景 | 技能 / 动作 | 输出位置 |
|------|-------------|----------|
| 制定 / 迭代功能规格 | 产品在产品仓：`/skill:requirement-forge` | 产品仓（经 submodule 呈现为 `requirements/`） |
| 工程侧取最新规格 | `git submodule update`（按约定钉 commit）并提交父仓 gitlink | `.lattice/requirements` gitlink |
| 设计蓝图 | `/skill:design-blueprint`（**只读**指定 `requirements/` 下 feature） | `context/` |
| 代码实现 | `/skill:code-forge`（指定 `context/` 设计锚点） | `backend/` + `web/` |
| 代码审查 | `/skill:review` | `reviews/review-log.md` |
| 经验沉淀 | `/skill:learning-harvest` | `learnings/operational-learnings.md` |
| 角色协作指南 | — | [docs/workflows/](../docs/workflows/) |
