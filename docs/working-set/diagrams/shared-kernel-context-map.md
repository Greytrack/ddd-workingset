# Shared Kernel 与四个业务 Bounded Context

- **文档状态**：Reviewed
- **约束状态**：`AC-001 Confirmed`
- **设计阶段**：阶段 3.0 企业架构外框
- **依据**：`USER-ARCH-2026-11-09` 的明确说明及两张原始架构示意图
- **相关文档**：[Shared Kernel 边界](../domain/shared-kernel-boundary.md)、[候选 Context Map](../domain/context-map.md)
- **范围**：实线表达已确认的依赖方向；虚线只表达待设计的局部协作，不表示部署、聚合包含或共同事务。

```mermaid
flowchart TB
    subgraph BusinessContexts["Business Bounded Contexts"]
        GateFlow["GateFlow BC"]
        ALM["ALM BC"]
        DQA["DQA BC"]
        PLM["PLM BC"]
    end

    subgraph SharedKernel["Shared Kernel - AC-001 Confirmed"]
        ClassHierarchy["Class Hierarchy"]
        Privilege["Privilege"]
        Workflow["Workflow"]
        WorkingSet["Working Set<br/>本设计聚焦能力"]
    end

    GateFlow -->|"消费公共工作集能力"| WorkingSet
    ALM -->|"消费公共工作集能力"| WorkingSet
    DQA -->|"消费公共工作集能力"| WorkingSet
    PLM -->|"消费公共工作集能力"| WorkingSet

    WorkingSet -.->|"局部协作待定义"| ClassHierarchy
    WorkingSet -.->|"授权协作待定义"| Privilege
    WorkingSet -.->|"流程协作待定义"| Workflow
```

## 明确不表达

- 不表示 Shared Kernel 是一个单体服务、单一限界上下文或单一聚合；
- 不表示四个业务 BC 共享同一个业务对象模型；
- 不表示 Working Set 可以绕过业务 BC 直接写 Requirement、Part 或其他专有对象；
- 不确认 `create / extend / resolve / diff / merge` 的最终接口签名、同步方式或事务边界。
