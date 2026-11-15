# T7 后续实施计划

**状态：Draft（T7-E 契约已裁定 C；T7-F 待推进）**。已实现的创建任务、范围绑定、受理快照和索引见 [T7 设计](../domain/working-set-creation-lro-design.md)及[验收索引](README.md)。

## T7-E 关系与引用构建

QUESTION-010 已于 2026-11-15 裁定 C，见[裁定记录](../reviews/2026-11-15-question-010-relation-identity-decision.md)。

- **Reference**：逻辑 Family（`targetCommonId` + `targetTrackerId`），可选 `targetRevision` 钉版和 `targetLocalId` 物理定位；右侧为空保持有效悬空，修复同一行。
- **Association**：具体 `sourceLocalId`/`targetLocalId`；缺端点不落库，保留恢复意图；下游关系丢弃。
- 本轮只消费 `TrackerItemsIndexed` 做构建与修复，不参与 Compare/Merge（D6）；权限保持 D11 占位。
- 记录可观察的构建/修复失败，并裁定关系进度是否进入任务视图。FACT-009 的可见性、删除守卫和 QUESTION-006 保留期限仍需随 T7-E 验收确认。

## T7-F 规模、保留与恢复

验证受理快照耗时、并发创建与连接池压力、批大小、失败明细规模、保留与清理。补验证进程中断后的接续。规模决策见 [QUESTION-012](../domain/open-questions.md)，与 [T6-E](t6-next-stage-outline.md) 的 Merge 规模和回填范围协调。

实施与验证遵循 [AGENTS.md](../../../AGENTS.md)。各片的场景、验收与裁定登记见 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)和 [BASELINE](../BASELINE.md)。
