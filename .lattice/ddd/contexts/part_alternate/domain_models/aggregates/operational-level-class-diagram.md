# Operational Level - 运行时模型

## 说明

运行时模型是**继承已合并后的只读快照**，没有修改、新增、删除操作，没有继承逻辑。

三个聚合根各自持有完整的、已合并的数据：`SubClass` 的页签、属性、生命周期已经是继承合并后的结果，不需要再走 `ClassHierarchyService`。

---

## 运行时元数据

### 类图

```mermaid
classDiagram
    class CommonClass {
        <<interface>>
        +ClassId getId
        +ClassId getParentClassId()
        +ClassId getBaseClassId()
        +ApiId apiId
        +String name
        +boolean isEnabled       
        +List~TableDesc~ getTableDescs()            
    }

    class BaseClass {
        <<aggregate>>
        +List~TableDesc~ tableDescs           
        +List~ClassGroup~ classGroups
    }

    class Clazz {
        <<aggregate>> 
        +List~TableDesc~ tableDescs     
        
    }

    class SubClass {
        <<aggregate>>   
        +NumberingRuleId numberingRule
        +VersionRuleId versionRule
        +WorkflowId workflowId
        +boolean addFileRequired
        +boolean cadMappingRequired
        +Integer tabDisplay
        +boolean hasGlobalTracker
        +TrackerId globalTrackerId
        +List~TableDesc~ tableDescs
        +List~Attribute~ attributes
        +List~Lifecycle~ lifecycles
        
        +Void createObject(Object objectType,CreateContext createContext)
        
    }

    CommonClass <|-- BaseClass : extends
    CommonClass <|-- Clazz : extends
    CommonClass <|-- SubClass : extends

    class TableDesc {
        <<value object>>
        +TableId tableId
        +ApiId apiId
        +String name
        +DisplayLocation displayLocation
        +boolean isChangeControlled
        +boolean isEditable
        +boolean isVisible
        +boolean isRowNotEmpty
        +List~Attribute~ columns
    }

    class Attribute {
        <<value object>>
        +AttributeId attributeId
        +ApiId apiId
        +String name
        +String dbName
        +AttributeType attributeType
        +DataType dataType
        +boolean isReadOnly
        +boolean isRequired
        +boolean isVisible
        +boolean isBuildIn
        +boolean isReference
        +Object defaultValue
        +Long source
        +boolean isChangeControlled
        +Long canSort
        +Long canJump
        +boolean canMerge
        +boolean canSuspected
        +Integer displayNameFormat
        +String tips
        +Integer columnWidth
        +Integer columnHeight
        +String icon
        +Integer displayLocation
        +Long showStyle
        +String attrConfig
        
        +boolean validate(Object value)
    }
    class ClassRuntimeQueryService {
        <<interface>>
        +BaseClass getBaseClass(ClassId)
        +Clazz getClazz(ClassId)
        +SubClass getSubClass(ClassId)        
    }

    class TextAttribute {
        <<value object>>
        +Long textRuleId
        +Long maxInputLength
    }

    class NumericalAttribute {
        <<value object>>
        +Long decimalLength
        +boolean canZero
        +Long minNumberValue
        +Long maxNumberValue
    }

    class ListAttribute {
        <<value object>>
        +Long enumListId
        +boolean canMultipleSelect
        +String multiSelectConditions
    }

    class DateAttribute {
        <<value object>>
        +Long dateRule
        +Long dateShowStyle
    }

    class CompositeAttribute {
        <<value object>>
        +Long enumListId
    }

    class ImageAttribute {
        <<value object>>
        +boolean canMultipleSelect
        +String formatLimit
        +Long maxUploadFileSize
        +Long maxUploadFileNumber
    }

    class ReferenceAttribute {
        <<value object>>
        +Long referenceType
        +String referenceLeftField
        +String referenceRightField
        +Long referenceAttributeId
    }

    class AutoNumberAttribute {
        <<value object>>
    }

    Attribute <|-- TextAttribute
    Attribute <|-- NumericalAttribute
    Attribute <|-- ListAttribute
    Attribute <|-- DateAttribute
    Attribute <|-- CompositeAttribute
    Attribute <|-- ImageAttribute
    Attribute <|-- ReferenceAttribute 
    Attribute <|-- AutoNumberAttribute

    class Lifecycle {
        <<value object>>
        +LifecycleId lifecycleId
        +ApiId apiId
        +String name
        +boolean isEnabled      
    }   

    class ClassGroup {
        <<value object>>
        +ClassGroupId groupId
        +String name
        +ApiId apiId
        +List~ClassId~ attachedSubClassIds
    }    
    BaseClass "1" --> "*" ClassGroup : has
    CommonClass "1" --> "*" TableDesc : has
    SubClass "1" --> "*" Attribute : has
    SubClass "1" --> "*" Lifecycle : has
    TableDesc "1" --> "*" Attribute : columns
    
```

---

### 简略的对象图

```mermaid
flowchart LR
    subgraph BS["BASECLASS"]
        direction TB
        BCB["基类: BASECLASS"]
    end 
    
    subgraph BAT["TableDesces"]
        TB2B["概览:TableDesc"]             
    end    
    
    subgraph BAA["Attributes"]
        BC3A["当前页签的属性:Attribute"]
    end
    
    BS --> BAT --> BAA
    
    subgraph SubClass
        direction TB
        S["name: 部件<br/>baseClassId: 1001<br/>"]
    end
    
    subgraph Attributes
        TC3A["当前页签的属性:Attribute"]
    end


	subgraph Class
        direction TB
        CB["类: CLASS"]
    end 
    
    subgraph TableDesces
        TC2B["类属性页签:TableDesc"]     
        TC2D["BOM:TableDesc"]        
    end    
    
    subgraph SubClass
        direction TB
        S["name: 部件<br/>baseClassId: 1001<br/>"]
    end
    
    subgraph Attributes
        TC3A["当前页签的属性:Attribute"]
    end

    subgraph T1["Attributes"]
        T1A["概览属性:Attribute"] 
        T1B["类属性:Attribute"]
        T1C["特征属性:Attribute"]        
        
    end

    subgraph T2["TableDesces"]
        T2A["概览:TableDesc"]
        T2B["类属性页签:TableDesc"]
        T2C["特征属性页签:TableDesc"]
        T2D["BOM:TableDesc"]        
    end

    subgraph T3["Attributes"]
        T3A["当前页签的属性:Attribute"]
    end

    
	CB --> TableDesces --> TC3A

    S --> T1
    S --> T2 --> T3
 

    style S fill:#e3f2fd,stroke:#1565c0
    style T1 fill:#f3e5f5,stroke:#7b1fa2
    style T2 fill:#f3e5f5,stroke:#7b1fa2
    style T3 fill:#f3e5f5,stroke:#7b1fa2


```

**说明：** 部件 SubClass 的概览页签（名称、编号）继承自 BaseClass，属性页签（材料）继承自 Class，特征页签（重量）是 SubClass 自己的。三个页签在运行时已合并为扁平快照，不再需要走继承链。

## 运行时的实例化

运行时实例模型是数据对象在运行时的快照，包含属性值、页签行、版次、工作流等状态。

```mermaid
classDiagram
    class DataObject {
        <<interface>>
        +DataObjectId getId()
        +String getNumber()
        +String getName()
        +Object getValue(AttributeId)
        +Object getValue(String apiId)
        +void setValue(AttributeId, Object)
        +void setValues(Map~Object, Object~)
        +void delete()
        +void undelete()
        +boolean isDeleted()
        +DataObject saveAs(ObjectType, Map)
        +SubClass getSubClass()        
        +Long getWorkSpace()
        +Long getWorkingset()
    }

    class Revisions {
        <<interface>>
        +DataObject checkIn()
        +DataObject checkOut()
        +DataObject cancelCheckout()
        +boolean isCheckedOut()
        +List~Revision~ getRevisions()
        +Revision getLatestRevision()
        +DataObject switchRevision(Object param)
        +boolean isLatestRevision()
        +DataObject getLatestObject()
        +String getCheckoutUser()
        +Long getCheckoutTime()
        +String getCheckinUser()
        +Long getCheckinTime()
        +Long getReleaseTime()
        +boolean isCheckOut()
        +String getVersion()
        +Integer getRevisionNumber()
    }

    class Routable {
        <<interface>>
        +void submit()
        +void approve(String comment, String content)
        +void reject(String comment, String content)
        +void revoke(String comment, String content)
        +void transfer(User)
        +void addApproveUsers(WorkflowStatus, List~User~, boolean)
        +void removeApproveUsers(WorkflowStatus, List~User~)
        +void addAcknowledgers(List~User~)
        +void removeAcknowledgers(List~User~)
        +void review()
        +void acknowledge(String comment, String content)
        +void changeStatus(WorkflowStatus)
        +void changeStatus()
        +void urgeTodo()
        +WorkflowStatus getCurrentStatus()
        +WorkflowStatus getPreviousStatus()
        +WorkflowStatus getNextStatus()
        +WorkflowStatus getStatus(String id)
        +List~WorkflowStatus~ getChangeStatusList()
        +List~Integer~ getApproveRounds()
        +List~DataObject~ getApproveRecordsByRound(Integer)
        +List~DataObject~ getPendingApprovers(WorkflowStatus)
        +List~DataObject~ getApprovers(WorkflowStatus, Integer, ApproveAction)
        +List~DataObject~ getAcknowledgers(ApproveAction)
    }

    class Part {
        <<aggregate root>>
        +DataObjectId id
        +String number
        +String name
        +List~Revision~ revisions
    }

    class Change {
        <<aggregate root>>
        +DataObjectId id
        +String number
        +String name
        +WorkflowStatus currentStatus
    }

    class Cell {
        <<value object>>
        +CellId getId()
        +Object getValue()
        +Attribute getAttribute()
        +AirList getAvailableValues()        
    }

    class Table {
        <<aggregate root>>
        +TableId getId()
        +TableDesc getTableDesc()
        +List~Attribute~ getAttributes()
        +Row createRow(Map~Object, Object~)
        +List~Row~ createRows(List~Object~)
        +void updateRows(Map~Object, Object~)
        +void removeRows(List~Object~)
        +Row getRow(Long)
        +List~Row~ getRows()
        +PageData getRows(PageQuery)
        +DataObject getParent()
    }

    class Row {
        <<entity>>
        +RowId getId()
        +Cell getCell(Object attr)
        +List~Cell~ getCells()
        +Integer getActionType()
        +Row getOldOperationRow()
        +DataObject getReferent()
        +AirObject getParent()
    }

    class Revision {
        <<value object>>
        +RevisionId getId()
        +String getCheckoutUser()
        +Long getCheckoutTime()
        +String getCheckinUser()
        +Long getCheckinTime()
        +Long getReleaseTime()
        +boolean isCheckOut()
        +String getVersion()
        +Integer getRevisionNumber()
    }

    class WorkflowStatus {
        <<value object>>
        +StatusId getId()
        +String getApiId()
        +String getName()
        +Integer getRank()
    }

    class ApproveAction {
        <<enum>>
        +APPROVE
        +REJECT
        +REVOKE
        +ACKNOWLEDGE
    }

    class PageQuery {
        <<value object>>
        +int page
        +int pageSize
        +OrderBy order
    }

    class PageData {
        <<value object>>
        +List~Row~ rows
        +long total
    }

    class DataObjectQueryService {
        <<service>>
        +DataObject getDataObject(DataObjectId)
        +Table getTable(DataObjectId, TableId)
        +List~Table~ getTables(DataObjectId)
    }

    class DataObjectService {
        <<service>>
        +DataObjectId createDataObject(ClassId, Map~AttributeId, Object~)
    }

    DataObject <|.. Part : implements
    Revisions <|.. Part : implements
    DataObject <|.. Change : implements
    Routable <|.. Change : implements

    DataObjectQueryService --> DataObject : queries
    DataObjectQueryService --> Table : queries
    Table "1" --> "*" Row : has
    Row "1" --> "*" Cell : has
    Cell "1" --> "1" Attribute : refers to
    Table "1" --> "1" TableDesc : refers to
```

### 接口与实现分离

```
                    ┌─────────────────────────┐
                    │   DataObjectQueryService│  ← 领域服务
                    │   getTable()            │
                    │   getCell()             │
                    │   getRow()              │
                    └────────┬────────────────┘
                             │ 跨聚合根查询
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
 ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
 │   DataObject    │ │    Revisions    │ │    Routable     │  ← 接口
 └────────┬────────┘ └────────┬────────┘ └─────────────────┘
          │ 实现              │ 实现
 ┌────────┴────────┐ ┌────────┴────────┐
 │     Part        │ │    Table        │  ← 聚合根
 │  + attributes   │ │  + rows         │
 │  + revisions    │ │  + createRow()  │
 └─────────────────┘ └─────────────────┘
```






**说明：** 部件 SubClass 的概览页签（名称、编号）继承自 BaseClass，属性页签（材料）继承自 Class，特征页签（重量）是 SubClass 自己的。三个页签在运行时已合并为扁平快照，不再需要走继承链。

---

## 与 Knowledge Level 的对比

| 维度 | Knowledge Level | Operational Level |
|------|---------------|-------------------|
| 角色 | 定义层 | 运行时 |
| 继承 | 由 ClassHierarchyService 处理 | 无需处理，已合并 |
| 操作 | 增删改查 | 只读查询 |
| 关系 | 实体（有身份）+ 引用 by ID | 值对象（无身份，快照） |
| 仓储 | 有 | 直接查缓存 |
| 事务 | 有 | 无 |
| 覆盖 | AttributeOverride 等 | 已合并进属性值 |
| 数据来源 | DB | 运行时缓存 |
