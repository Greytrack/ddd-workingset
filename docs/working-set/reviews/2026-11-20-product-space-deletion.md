# 产品空间删除入口与清理边界

**状态：Reviewed。** 2026-11-20 用户明确裁定仅在默认工作集删除空产品空间，并在删除流程中处理绑定，撤除独立的产品空间与 Tracker 解绑入口。

旧路径要求先手动解除绑定，再删除产品空间。用户裁定采用单一删除入口；同步清理会在一个事务中修改多个聚合，因此按 TC-002 通过可靠事件逐项提交。空 Tracker 属于空间的容器范围；外部 Tracker 引用和保留的业务引用会阻止清理。

当前契约与场景见 [T2 §4](../domain/binding-deletion-contract-v1.md#42-绑定移除) 和 [SCN-064](../scenarios/core-scenarios.md#scn-064)。测试证据见 `BindingAcceptanceTest`、`TrackerDefaultAcceptanceTest` 与 `ModuleEntrypointsTest`。子工作集删除此次仅迁移 HTTP 适配器，既有事务问题继续按 [TC-002 整改记录](2026-11-19-ddd-anti-pattern-scan.md)跟踪。
