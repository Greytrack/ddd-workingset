# relationrow（关系行公共标识）— model.md

> 模块归属：`shared-kernel`（公共契约，不迁往需求 BC）。对应设计稿 `10.引用场景约束v1.md` 边界 5 / 触发口径 10。

## 1. 职责

跨关系类型（引用、关联、将来 BOM）的**行标识契约**：清标记、看差异、待查清单按 `RelationRowId(relationType, rowId)` 定位一行，公共链路不认识具体关系聚合。

## 2. 元素

| 元素 | 形态 | 说明 |
|------|------|------|
| `RelationType` | 枚举 | 关系行类型（REFERENCE / ASSOCIATION；新关系在此登记） |
| `RelationRowId` | 值对象 | `relationType ＋ rowId`；由各关系聚合把自身 id 包成它，转换只在聚合内发生 |

## 3. 消费方

| 消费方 | 用途 |
|--------|------|
| `PropagationSuspectHolder` | `relationRowId()` 返回自身标识 |
| `PropagationSuspectHolderRepository` | `findRowsByIds(List<RelationRowId>)` 按标识取行 |
| 公共传播怀疑服务（v4-alm，7 号稿） | 清标记 / 差异定位 |

## 4. 约束

- 无 I/O、无 Spring 注解：纯值对象。
- v3 裸 Long「先查引用表再查关联表」的二义性定位由本标识显式化替代，对外效果不变。

## 5. 待办

1. 关联行（Association 聚合）接入时验证复用面。
