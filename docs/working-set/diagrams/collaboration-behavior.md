# Checkpoint 适用性与 GateFlow 内容切换

**状态：Draft / Candidate。** 业务结果与候选实施范围分别见下节。

早期 Merge Session 与 VIRTUAL 固定来源图已退出本页。当前合并时序见 [T1 MergeEntry 生命周期](merge-entry-lifecycle.md)，首次 COW 见 [T3 物化图](lighttrackeritem-materialization.md)。原场景、反例及裁定仍见 [B01～B12](../scenarios/shared-kernel-boundary-scenarios.md)和[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)。共享权威点查与列表投影时效仍见 [QUESTION-003](../domain/open-questions.md)。

## 用户主动比对与 Checkpoint 适用性

以下分支表达 SCN-009 已确认的业务规则。S7 中的 Checkpoint 映射及验证见 [S7 记录](../implementation/t5-s7-verification-result.md)。

```mermaid
flowchart TD
    Start["用户主动发起比对"] --> Match{"存在适用的已处理 Checkpoint<br/>且双侧业务版次均相同"}
    Match -->|"是"| Hide["抑制对应已处理差异<br/>普通编辑增加乐观锁 lock_version 也不改变此结果"]
    Match -->|"任一侧业务版次已升级"| Expired["旧 Checkpoint 对新版本组合过时"]
    Expired --> Compare["重新比较所有适用字段<br/>包含曾合并与未合并的字段"]
    Match -->|"无适用 Checkpoint"| Compare
    Compare --> Result["显示实际仍存在的差异"]
```

## GateFlow 草稿内容整体切换

**业务结果已由用户确认，内部协议为 Candidate。** 目的为表达 V3 的可见内容与准备内容的区别。依据 [GateFlow 场景](../scenarios/shared-kernel-boundary-scenarios.md#gateflow-草稿版次内容替换) 和 [职责边界](../domain/shared-kernel-boundary.md#gateflow-process-与版次用户已确认)。此图不规定数据库事务或聚合组成。准备期间已确认允许编辑，切换时可覆盖期间编辑。内容范围已固定为过程元素业务对象、裁剪问题、裁剪答案、合规映射四类，不纳入共享 Tracker。内部业务细节属于 GateFlow 专项。切换瞬间的并发提交、重试和取消协议待设计。

```mermaid
flowchart TD
    Start["GateFlow 请求以 V1 内容替换 V3 草稿"] --> Prepare["准备完整替换内容<br/>V3 使用当前完整内容并允许编辑"]
    Prepare --> Ready{"是否全部准备成功"}
    Ready -->|"否或准备失败"| Old["V3 保留当前内容及期间已保存编辑"]
    Ready -->|"是"| Switch["整体切换 V3 当前内容<br/>期间编辑可被覆盖"]
    Switch --> New["V3 内容完整对应 V1<br/>V3 独有流程元素退出当前内容"]
    New --> Identity["V3 版次身份和工作集 ID 保留<br/>父级仍为 V2"]
```
