# T1 契约审核记录：Merge 执行行为契约 v1

**状态：Reviewed（2026-11-11）；结论：T1 通过并关闭。重试与终态话题不再新增领域问题。**

审核对象：[Merge 执行行为契约 v1](../domain/merge-execution-contract-v1.md)（Draft → Reviewed）、[ADR-0005](../adr/0005-merge-execution-model.md)（Accepted，含 2026-11-11 补充裁定）、[MergeEntry 生命周期图](../diagrams/merge-entry-lifecycle.md)、SCN-055～060。

审核方式：逐节核对契约与已确认裁定、行为矩阵与状态机、场景与契约条款对应、图表与文字一致性、交叉引用与残留旧表述。**未做**业务方访谈、实现验证与 Mermaid 实际渲染。

## 1. 审核结论

**通过并关闭**。T1 的四项裁定（Q1～Q4）已全部落实到契约、ADR 补充裁定、图与场景；行为矩阵、状态机、终态判定与 `ApplyResult` 语义自洽；未发现阻塞性矛盾或未授权状态提升。

- **重试与终态规则关闭**（Q4，方案 B）：一轮一次执行即终态、不可重开、未完成项须新建 MergeRound。**不再围绕 MergeRound 重试新增领域问题。**
- 纯 `DEFER` 轮次按现行判定归 `Failed`，不新增专用状态枚举；该结论作为已关闭项记录，不再作为待裁定问题提出。

## 2. 审核项与结果

| 检查项 | 结果 |
| --- | --- |
| 契约 ↔ ADR-0005（含补充裁定）一致性 | 通过：Q1～Q4 在契约 §10/§11 与 ADR 中表述一致，ADR 状态保持 Accepted |
| MergeRound 行为完整性 | 通过：`startRound`／`registerEntries`／`executeEntry`／`reportProgress`／`completeRound` 具备入参、返回、前置与失败语义；`completeRound` 返回本轮唯一 `ApplyResult` |
| MergeEntry 状态机闭合 | 通过：8 态（`Compared`／`Decided`／`Applying`／`Applied`／`Rejected`／`StaleEvidence`／`Deferred`／`Confirmed`），终态唯一可达，无非法迁移（`Deferred`→`Confirmed` 禁止） |
| MergeRound 状态机闭合 | 通过：`Preparing` → `Running` → `Completed`，终态不可重开；三态判定与用户裁定一致 |
| 行为矩阵覆盖 | 通过：`ACCEPT`／`KEEP_TARGET` × `markAsMerged` 四组合 + `DEFER` 两组合 + BC 拒绝 + 证据过期共 8 行 |
| `ApplyResult` 语义 | 通过：本轮 `summary` + `itemResults` 的不可变终态快照；新轮次不修改旧轮次结果 |
| `KEEP_TARGET + markAsMerged=true` | 通过：明确计入成功处理项，不写内容、不伪造相等，且不在原轮次重试 |
| 纯 `DEFER` / 空轮次 | 通过：归 `Failed`，不扩展枚举；已在契约 §8 显式记录 |
| 场景覆盖 | 通过：SCN-055/056（已确认规则）、057（过期审阅）、058（撤销晚到）、059（`DEFER` 残留与终态）、060（部分成功后新轮次）与契约条款一一对应 |
| 图与文字一致性 | 通过：时序图、MergeEntry 状态机、MergeRound 终态图与契约 §4/§5/§8 一致；旧「同轮重试」表述已清除（仅保留作废说明与断言） |
| 不新增结构 | 通过：未引入 Merge Session／Record／Manifest／ItemVariant，未新增聚合 |
| 追溯链 | 通过：D-019、FACT-027、INV-061～063、ADR-0005、SCN-055～060 双向可追溯；INV-063 验证列已补 SCN-059/060 |
| 旧表述残留 | 通过：全库检索仅命中「早期设计作废说明」「断言不存在」「B08 已确认裁定加注」「Q4 裁定行」等正确用法 |
| Mermaid 渲染 | **未验证**：环境无 `mmdc`；静态检查通过（fence 配对、通用语法），发布前须复核 |

## 3. 关闭声明

以下话题**关闭**，不再作为领域问题提出：

1. MergeRound 是否支持同轮重试——不支持（Q4 方案 B）。
2. 终态轮次能否重开或继续登记——不能。
3. `ApplyResult` 是否跨轮次累计——不累计，只汇总本轮。
4. 未完成条目如何重做——重新 `compare` + 新建 MergeRound。
5. 纯 `DEFER` / 空轮次的终态——按现行判定归 `Failed`，不新增枚举（若未来业务需要，另立新议题，不在本次重开）。

## 4. 遗留（非领域问题，不阻塞 T2）

以下属执行协议的实现形式，已登记于 `QUESTION-015`，**不是**本次关闭范围内的领域问题：

- `ComparisonToken` 的编码与存储形式；
- 幂等键的具体形式（含重复投递去重）；
- 乱序确认的实现机制（因果单调的落地方式）；
- `MergeCheckpoint` 的「业务 Revision」字段映射细化。

以上留待实现阶段或相应实现评审，不改变本审核结论。

## 5. 下一步

T1 关闭，进入 **T2：绑定与删除契约**（SpaceBinding／TrackerBinding 内部结构与键、软删 vs 彻底删除、级联边界、默认 WorkingSet 约束）。
