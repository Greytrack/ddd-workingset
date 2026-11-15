# T7 后续实施计划

**状态：Draft（T7-E／F）**。已实现的创建任务、范围绑定、受理快照和索引见 [T7 设计](../domain/working-set-creation-lro-design.md)及[验收索引](README.md)。

## T7-E 关系与引用构建

以索引完成事件 TrackerItemsIndexed 作为协作边界。关系身份、钉版、悬空及修复依赖 [QUESTION-010](../domain/open-questions.md)。确认这些规则后再实现关系协作端口与消费者、记录可观察的构建失败，并裁定关系进度如何进入任务视图。

## T7-F 规模、保留与恢复

验证受理快照耗时、并发创建与连接池压力、批大小、失败明细规模、保留与清理。补验证进程中断后的接续。规模决策见 [QUESTION-012](../domain/open-questions.md)，与 [T6-E](t6-next-stage-outline.md) 的 Merge 规模和回填范围协调。

实施与验证遵循 [AGENTS.md](../../../AGENTS.md)。各片的场景、验收与裁定登记见 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)和 [BASELINE](../BASELINE.md)。
