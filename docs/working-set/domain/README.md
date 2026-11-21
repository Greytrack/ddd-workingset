# Domain

**状态：Draft（目录用途；不接受任何具体领域结论）**

存放统一领域语言、业务事实、业务不变量、领域模型和证据来源。当前规则按 [BASELINE](../BASELINE.md)
的接受范围读取；本目录不重复裁定历史，也不把候选资料自动升级为 Accepted。

## 当前权威入口

- [统一领域语言](glossary.md)：术语及其边界；
- [聚合边界](aggregate-model.md)：聚合、非聚合共享能力与一致性边界；
- [Shared Kernel 边界](shared-kernel-boundary.md)：企业外框、内部模块与跨 BC 读取边界；
- [跨 BC 公共契约](cross-bc-contract-v1.md)：只读能力、事件协作和九分类失败语义；
- [Merge 执行](merge-execution-contract-v1.md)：轮次、条目、比较证据与确认；
- [物化协议](lighttrackeritem-materialization-contract-v1.md)：身份、双态、COW 与失败语义；
- [绑定与删除](binding-deletion-contract-v1.md)：绑定结构、删除和拒绝条件；
- [多级 WorkingSet 谱系](working-set-lineage-design.md)、[工作集创建 LRO](working-set-creation-lro-design.md)；
- [事实与政策基线](facts-policy-baseline-v1.md)：已接受的事实与政策主题（Accepted）；
- [不变量](invariants.md)、[开放问题](open-questions.md)、[业务场景](../scenarios/README.md)。

## 候选与历史

- [facts.md](facts.md)：候选事实、政策、假设与技术约束的混合登记；
- [evidence-register.md](evidence-register.md)：来源、读取范围和确认状态；
- [context-map.md](context-map.md)：候选战略上下文；
- [model-baseline-candidate-v1.md](model-baseline-candidate-v1.md)：Historical，首版模型候选；
- [business-object-lifecycle-design.md](business-object-lifecycle-design.md)：Historical；当前生命周期契约以 T4 为准。

上述文件的状态和接受范围以 [BASELINE](../BASELINE.md) 为准；文件级 Draft 不取消已登记的条款确认。
