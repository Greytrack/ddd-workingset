# TrackerItem Shared Kernel Java Demo

> 本文为旧版示意代码，不是已实现的 V4 协议。V4 以 [workingsetModel](./workingsetModel/00-overview.md) 为准；本轮不将代理强行加入所有业务类型。

> 新增简例见 [V4 04：LightTrackerItemProxy、RequirementFactory、RequirementQueryModel 与命令](./workingsetModel/04-query-command-demo.md)。无需阅读本页旧版长示例即可理解当前装配方式。

> **代理适用边界**：本文强类型 Resolver 路径仍可作为思路参考；`implements TrackerItem` 不会自动创建代理、索引或启用 COW。通用代理须由加载入口显式组装，只能承载已声明的共享契约，不能直接返回为 Requirement。LightTrackerItem 保持独立持久化记录，不改名为 Shadow，也不注入加载服务。类型注册、两种读取路径及统一写保护见 [V4 §4.1](./workingsetModel/02-use-cases.md#loading-and-proxy)；旧示例的聚合归属、历史切点和事务实现须按 V4 重新验证，不能原样作为生产代码。

> **文档版本**: v2.21
> **最后更新**: 2026-10-27
> **性质**: Java 17 参考代码；展示完整协作契约与关键路径，未接入现网 DataObject、Repository 或事务设施。示例中的存储/锁/权限端口须按本文契约实现，不能把协议声明当作已有实现。

## 1. 业务使用方式

业务 BC 实现稳定的 TrackerItem 能力，并提供 TrackerItemContentPort；TrackerItemInstantiator 由 SK 实现。普通调用方只使用 TrackerItem、Requirement、Bug 及其列表，不接触 LightTrackerItem、来源指针或内部状态枚举；isInstantiated() 仅作为可选只读观察。

```java
List<Requirement> requirements = workingSetItems.resolveAll(
        workingSetId, requirementIds, Requirement.class
);

// 写用例由 biz Management 持有事务；SK 自动准备 COW 后交给业务行为。
workingSetItems.mutateAll(
        workingSetId, requirementIds, Requirement.class,
        items -> items.forEach(item -> item.revise("新标题", "新描述"))
);
```

resolveAll 返回当前工作集身份的完整业务对象；VIRTUAL 时在内存中组装，不创建物理行。查询对象是与持久化会话分离的对象，修改它不会自动写回。mutate/mutateAll 在同一工作集写协议内加载可写对象并保存，调用方无需主动实例化。此 demo 使用显式业务回调，不承诺自动脏检测 Unit of Work。

## 2. SK 公开模型与类型 SPI

### 2.1 身份和基础能力

主键保持 AirEdge 的 Long 约定。TrackerItemIdentity 是方便组装的不可变值表示，不另立一套唯一键；全局实例身份仍是 CommonId/LocalId，TrackerId 表达归属，WorkingSetId 只在用例上下文中出现。

```java
public record TrackerItemIdentity(Long trackerId, Long commonId, Long localId) {
    public TrackerItemIdentity {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(localId, "localId");
    }
}

public record BusinessObjectData(Map<String, Object> fields) {
    public BusinessObjectData {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}

public enum DeletionState { ACTIVE, DELETED }

public interface InstanceIdentifiable {
    Long getCommonId();
    Long getLocalId();

    default boolean isOriginal() {
        return getCommonId().equals(getLocalId());
    }

    default boolean isDerived() {
        return !isOriginal();
    }
}

public interface Deletable {
    DeletionState getDeletionState();
    void delete();
    void undelete();
    // 实体校验彻底删除意图，实际数据清理由用例协调器执行。
    void delete2();
}

public interface BusinessObject extends InstanceIdentifiable, Deletable {
    BusinessObjectData getBusinessData();
}

public interface TrackerItem extends BusinessObject {
    Long getTrackerId();
    String getTrackerItemType();

    // 加载时只读事实；不用于跳过写保护或判断可直接保存。
    boolean isInstantiated();
}

public interface TrackerItemInstantiator {
    TrackerItem instantiate(TrackerItemLocator locator);

    <T extends TrackerItem> T instantiate(TrackerItemLocator locator, Class<T> type);

    <T extends TrackerItem> List<T> instantiateAll(
            Long workingSetId, List<Long> commonIds, Class<T> type);
}

```

BusinessObjectData 是通用内容交换值，不专属于 Tracker，因此普通 Part 也可使用。示例字段只有字符串；真实嵌套集合、引用、附件需由类型适配器 按 schema 转换并隔离可变状态，不能用浅 Map 复制冒充完整实例化。

### 2.2 实例化能力与内容访问端口

```java
public enum TrackerItemCapability { DERIVE, INSTANTIATE, DIFF, MERGE }

// SPI 输入：目标身份与已加载的目标归属，无来源/实例化状态。
public record TrackerItemTarget(
        TrackerItemIdentity identity, Long projectId, Long productSpaceId
) {
    public TrackerItemTarget {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(productSpaceId, "productSpaceId");
    }
}

// SPI 读取请求；具体扩展作者处理存储差异，普通业务调用方不使用它。
public record SourceReadRequest(
        Long sourceLocalId, int sourceRevision, Instant capturedAt
) {
    public SourceReadRequest {
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("sourceRevision must be positive");
        }
    }
}

public record TrackerItemSnapshot(
        String itemType, BusinessObjectData content, DeletionState deletionState
) {
}

public record SavedTrackerItem(int localRevision, DeletionState deletionState) {
    public SavedTrackerItem {
        if (localRevision < 1) {
            throw new IllegalArgumentException("localRevision must be positive");
        }
        Objects.requireNonNull(deletionState, "deletionState");
    }
}

// 访问完整业务内容的端口；resolve 是无写副作用的内存重建。
public interface TrackerItemContentPort<T extends TrackerItem> {
    // 批量读取 detached 本地对象与固定来源切点；不得回退到当前最新内容。
    Map<Long, T> loadLocalBatch(Collection<Long> localIds);
    Map<SourceReadRequest, TrackerItemSnapshot> loadSourceBatch(
            Collection<SourceReadRequest> requests);
    T resolve(TrackerItemTarget target, TrackerItemSnapshot source);
    // 当前 Management 事务内保存；返回实际版次和删除状态供索引同步。
    SavedTrackerItem save(T item);
    // 内部物理创建扩展，不决定来源/闭包；返回真实业务对象，无结果包装。
    T instantiateLocal(TrackerItemTarget target, TrackerItemSnapshot source);
    // 批量读取实际持久化摘要，版本信息不进入公开 TrackerItem 契约。
    Map<Long, SavedTrackerItem> loadSavedStateBatch(Collection<Long> localIds);
    // 清理该 LocalId 的类型版次及从属数据；全局索引/关系由 SK 协调。
    void deletePhysical(Long localId);
}

```

实现 TrackerItem 声明参与工作集能力；类型注册必须提供 TrackerItemContentPort（包括内部 instantiateLocal），满足 DERIVE/INSTANTIATE 的基础接入要求。DIFF/MERGE 只有注册了对应策略才可声明支持，不能凭空由 marker 接口推断。类型比较/合并策略的具体代码不在本 demo 范围。

VIRTUAL 对象可以在内存中存在，isInstantiated() 为 false；本地物理对象加载后为 true。该标记无 setter，不独立持久化，也不是写入许可；对象加载后可能过时。公开 TrackerItemInstantiator 负责闭包实例化，内容端口的 instantiateLocal 仅创建给定身份的物理内容，二者不能互相替代。内容版次仅在内部持久化摘要中传递。

## 3. Requirement 与 Bug 的业务行为

### 3.1 Requirement

```java
public final class Requirement implements TrackerItem {
    public static final String ITEM_TYPE = "requirement";

    private final TrackerItemTarget target;
    private final boolean instantiated; // 只读加载事实，不独立持久化。
    private String title;
    private String description;
    private DeletionState deletionState;

    private Requirement(TrackerItemTarget target, String title,
                        String description, DeletionState deletionState, boolean instantiated) {
        this.target = Objects.requireNonNull(target);
        this.instantiated = instantiated;
        this.title = Objects.requireNonNull(title);
        this.description = description;
        this.deletionState = Objects.requireNonNull(deletionState);
    }

    public static Requirement reconstitute(TrackerItemTarget target, String title,
                                          String description, DeletionState state,
                                          boolean instantiated) {
        return new Requirement(target, title, description, state, instantiated);
    }

    public static Requirement fromSnapshot(
            TrackerItemTarget target, TrackerItemSnapshot source) {
        return fromSnapshot(target, source, false);
    }

    public static Requirement fromSnapshot(
            TrackerItemTarget target, TrackerItemSnapshot source, boolean instantiated) {
        if (!ITEM_TYPE.equals(source.itemType())) {
            throw new IllegalArgumentException("Unexpected Requirement snapshot");
        }
        return reconstitute(target,
                (String) source.content().fields().get("title"),
                (String) source.content().fields().get("description"),
                source.deletionState(), instantiated);
    }

    public void revise(String title, String description) {
        ensureActive();
        this.title = Objects.requireNonNull(title);
        this.description = description;
    }

    public Long getProjectId() { return target.projectId(); }
    public Long getProductSpaceId() { return target.productSpaceId(); }

    @Override public Long getTrackerId() { return target.identity().trackerId(); }
    @Override public Long getCommonId() { return target.identity().commonId(); }
    @Override public Long getLocalId() { return target.identity().localId(); }
    @Override public String getTrackerItemType() { return ITEM_TYPE; }
    @Override public boolean isInstantiated() { return instantiated; }
    @Override public DeletionState getDeletionState() { return deletionState; }

    @Override
    public BusinessObjectData getBusinessData() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("title", title);
        fields.put("description", description);
        return new BusinessObjectData(fields);
    }

    @Override public void delete() {
        ensureActive();
        deletionState = DeletionState.DELETED;
    }

    @Override public void undelete() {
        requireDeleted();
        deletionState = DeletionState.ACTIVE;
    }

    @Override public void delete2() { requireDeleted(); }

    private void ensureActive() {
        if (deletionState != DeletionState.ACTIVE) {
            throw new IllegalStateException("Requirement is deleted");
        }
    }

    private void requireDeleted() {
        if (deletionState != DeletionState.DELETED) {
            throw new IllegalStateException("Requirement is not in recycle bin");
        }
    }
}
```

### 3.2 Bug（非版次控制对象）

```java
public enum BugState { OPEN, RESOLVED }

public final class Bug implements TrackerItem {
    public static final String ITEM_TYPE = "bug";

    private final TrackerItemTarget target;
    private final boolean instantiated; // 只读加载事实，不独立持久化。
    private BugState state;
    private String resolution;
    private DeletionState deletionState;

    public Bug(TrackerItemTarget target, BugState state, String resolution,
               DeletionState deletionState, boolean instantiated) {
        this.target = Objects.requireNonNull(target);
        this.instantiated = instantiated;
        this.state = Objects.requireNonNull(state);
        this.resolution = resolution;
        this.deletionState = Objects.requireNonNull(deletionState);
    }

    public static Bug fromSnapshot(TrackerItemTarget target,
                                   TrackerItemSnapshot source) {
        return fromSnapshot(target, source, false);
    }

    public static Bug fromSnapshot(TrackerItemTarget target,
                                   TrackerItemSnapshot source, boolean instantiated) {
        if (!ITEM_TYPE.equals(source.itemType())) {
            throw new IllegalArgumentException("Unexpected Bug snapshot");
        }
        return new Bug(target,
                BugState.valueOf((String) source.content().fields().get("state")),
                (String) source.content().fields().get("resolution"),
                source.deletionState(), instantiated);
    }

    public void resolve(String resolution) {
        ensureActive();
        if (state == BugState.RESOLVED) {
            throw new IllegalStateException("Bug is already resolved");
        }
        this.resolution = Objects.requireNonNull(resolution);
        state = BugState.RESOLVED;
    }

    public Long getProjectId() { return target.projectId(); }
    public Long getProductSpaceId() { return target.productSpaceId(); }
    @Override public Long getTrackerId() { return target.identity().trackerId(); }
    @Override public Long getCommonId() { return target.identity().commonId(); }
    @Override public Long getLocalId() { return target.identity().localId(); }
    @Override public String getTrackerItemType() { return ITEM_TYPE; }
    @Override public boolean isInstantiated() { return instantiated; }
    @Override public DeletionState getDeletionState() { return deletionState; }

    @Override public BusinessObjectData getBusinessData() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("state", state.name());
        fields.put("resolution", resolution);
        return new BusinessObjectData(fields);
    }

    @Override public void delete() {
        ensureActive();
        deletionState = DeletionState.DELETED;
    }

    @Override public void undelete() {
        requireDeleted();
        deletionState = DeletionState.ACTIVE;
    }

    @Override public void delete2() { requireDeleted(); }

    private void ensureActive() {
        if (deletionState != DeletionState.ACTIVE) {
            throw new IllegalStateException("Bug is deleted");
        }
    }

    private void requireDeleted() {
        if (deletionState != DeletionState.DELETED) {
            throw new IllegalStateException("Bug is not in recycle bin");
        }
    }
}
```

工作流 state 与 deletionState 保持正交。Bug 的 sourceRevision=1 不代表内容不可变；其历史读取端口仍必须使用 capturedAt。未提供时态或冻结能力时应拒绝异步历史派生。

## 4. 具体 BC 的实例化与内容适配

### 4.1 Requirement 适配实现

```java
@Component
@RequiredArgsConstructor
public final class RequirementTrackerItemAdapter
        implements TrackerItemContentPort<Requirement> {

    private final RequirementStore store;

    @Override public Map<Long, Requirement> loadLocalBatch(Collection<Long> ids) {
        return store.loadDetachedBatch(ids);
    }

    @Override public Map<SourceReadRequest, TrackerItemSnapshot> loadSourceBatch(
            Collection<SourceReadRequest> requests) {
        // 不可变已提交 Revision 的精确读取，不能改为读最新值。
        return store.readCommittedRevisionBatch(requests);
    }

    @Override public Requirement resolve(TrackerItemTarget target,
                                         TrackerItemSnapshot source) {
        return Requirement.fromSnapshot(target, source);
    }

    @Override public Requirement instantiateLocal(
            TrackerItemTarget target, TrackerItemSnapshot source) {
        Requirement item = Requirement.fromSnapshot(target, source, true);
        store.insertWithInitialRevision(item);
        return item;
    }

    @Override public SavedTrackerItem save(Requirement item) {
        return store.saveWithRevision(item);
    }

    @Override public Map<Long, SavedTrackerItem> loadSavedStateBatch(Collection<Long> ids) {
        return store.loadSavedStateBatch(ids);
    }

    @Override public void deletePhysical(Long localId) {
        store.deleteOwnedData(localId);
    }
}

// 内部存储适配端口；由 sdkImpl 通过 DataObject Framework 实现。
public interface RequirementStore {
    Map<Long, Requirement> loadDetachedBatch(Collection<Long> localIds);
    Map<SourceReadRequest, TrackerItemSnapshot> readCommittedRevisionBatch(
            Collection<SourceReadRequest> requests);
    SavedTrackerItem insertWithInitialRevision(Requirement item);
    SavedTrackerItem saveWithRevision(Requirement item);
    Map<Long, SavedTrackerItem> loadSavedStateBatch(Collection<Long> localIds);
    void deleteOwnedData(Long localId);
}
```

### 4.2 Bug 适配实现

```java
@Component
@RequiredArgsConstructor
public final class BugTrackerItemAdapter implements TrackerItemContentPort<Bug> {
    private final BugStore store;

    @Override public Map<Long, Bug> loadLocalBatch(Collection<Long> ids) {
        return store.loadDetachedBatch(ids);
    }

    @Override public Map<SourceReadRequest, TrackerItemSnapshot> loadSourceBatch(
            Collection<SourceReadRequest> requests) {
        if (requests.stream().anyMatch(r -> r.sourceRevision() != 1)) {
            throw new IllegalArgumentException("Bug sourceRevision must be 1");
        }
        return store.readAsOfBatch(requests);
    }

    @Override public Bug resolve(TrackerItemTarget target,
                                TrackerItemSnapshot source) {
        return Bug.fromSnapshot(target, source);
    }

    @Override public Bug instantiateLocal(
            TrackerItemTarget target, TrackerItemSnapshot source) {
        Bug item = Bug.fromSnapshot(target, source, true);
        store.insert(item);
        return item;
    }

    @Override public SavedTrackerItem save(Bug item) {
        store.save(item);
        return new SavedTrackerItem(1, item.getDeletionState());
    }

    @Override public Map<Long, SavedTrackerItem> loadSavedStateBatch(Collection<Long> ids) {
        return store.loadSavedStateBatch(ids);
    }

    @Override public void deletePhysical(Long localId) {
        store.deleteOwnedData(localId);
    }
}

public interface BugStore {
    Map<Long, Bug> loadDetachedBatch(Collection<Long> localIds);
    Map<SourceReadRequest, TrackerItemSnapshot> readAsOfBatch(
            Collection<SourceReadRequest> requests);
    void insert(Bug item);
    void save(Bug item);
    Map<Long, SavedTrackerItem> loadSavedStateBatch(Collection<Long> localIds);
    void deleteOwnedData(Long localId);
}
```

insert 按预分配 LocalId 幂等，并校验已存在行的身份与内容，不能遇到重复主键就静默视为成功。对象需要本地化的关系、附件和归属由对应 Store/适配器 在当前事务中处理，源行不会被修改。

### 4.3 类型注册与装配

```java
// 内部装配记录，不是第三个业务能力接口；不得暴露给普通业务调用方。
public record TrackerItemTypeExtension<T extends TrackerItem>(
        String itemType, Class<T> supportedClass,
        Set<TrackerItemCapability> capabilities,
        TrackerItemContentPort<T> contentPort
) {
    public TrackerItemTypeExtension {
        Objects.requireNonNull(itemType, "itemType");
        Objects.requireNonNull(supportedClass, "supportedClass");
        Objects.requireNonNull(contentPort, "contentPort");
        capabilities = Set.copyOf(capabilities);
        if (itemType.isBlank() || !capabilities.containsAll(Set.of(
                TrackerItemCapability.DERIVE, TrackerItemCapability.INSTANTIATE))) {
            throw new IllegalArgumentException("Missing core TrackerItem registration");
        }
        // 本 demo 没有 Diff/Merge 策略装配，拒绝虚假声明。
        if (capabilities.contains(TrackerItemCapability.DIFF)
                || capabilities.contains(TrackerItemCapability.MERGE)) {
            throw new IllegalArgumentException("Diff/Merge strategy registration is required");
        }
    }
}

public final class TrackerItemTypeRegistry {
    private final Map<String, TrackerItemTypeExtension<? extends TrackerItem>> extensions;

    public TrackerItemTypeRegistry(
            List<TrackerItemTypeExtension<? extends TrackerItem>> plugins) {
        extensions = plugins.stream().collect(Collectors.toUnmodifiableMap(
                TrackerItemTypeExtension::itemType, Function.identity()));
    }

    public TrackerItemTypeExtension<? extends TrackerItem> require(String itemType) {
        var extension = extensions.get(itemType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported item type: " + itemType);
        }
        return extension;
    }

    @SuppressWarnings("unchecked")
    public <T extends TrackerItem> TrackerItemTypeExtension<T> require(
            String itemType, Class<T> expectedClass) {
        var extension = require(itemType);
        if (!expectedClass.isAssignableFrom(extension.supportedClass())) {
            throw new IllegalArgumentException("Incompatible business type");
        }
        // 注册表校验输出类型后，在单一边界完成泛型擦除转换。
        return (TrackerItemTypeExtension<T>) extension;
    }
}

// Apps 装配示意：适配类可以同时实现内容契约，无须额外增加业务服务。
@Configuration
public class TrackerItemExtensionConfiguration {
    @Bean
    public TrackerItemTypeRegistry trackerItemTypeRegistry(
            RequirementTrackerItemAdapter requirements, BugTrackerItemAdapter bugs) {
        TrackerItemTypeExtension<Requirement> requirementExtension =
                new TrackerItemTypeExtension<>(Requirement.ITEM_TYPE, Requirement.class,
                        Set.of(TrackerItemCapability.DERIVE, TrackerItemCapability.INSTANTIATE),
                        requirements);
        TrackerItemTypeExtension<Bug> bugExtension =
                new TrackerItemTypeExtension<>(Bug.ITEM_TYPE, Bug.class,
                        Set.of(TrackerItemCapability.DERIVE, TrackerItemCapability.INSTANTIATE),
                        bugs);
        return new TrackerItemTypeRegistry(List.of(requirementExtension, bugExtension));
    }
}

```

注册表没有 Requirement/Bug 类型分支；itemType、supportedClass 和 capabilities 属于装配元数据，不挤入单职责 SPI。公开实例化直接返回 TrackerItem/T 或 List<T>，无独立结果包装。TrackerItem.class 允许读取异构条目；Requirement.class 仅允许对应业务类型。

BC Adapter 只实现内容端口，不实现公开 Instantiator。SK 的 Instantiator 负责跨聚合协调，内部 LineageInstantiationService 复用闭包规划和原子索引迁移，ContentPort.instantiateLocal 只按确定来源复制给定目标。后续 Diff/Merge 策略独立注册，不加入 Instantiator 的方法列表。

Store 的本地对象加载必须将只读标记重建为 true；resolve 来源重建为 false；instantiateLocal 创建成功才返回 true 的对象。保存仍需依赖受管写上下文验证物理存在性、身份及版本，不能信任调用方标记。

## 5. SK 内部 LightTrackerItem

LightTrackerItem 在 VIRTUAL 和 INSTANTIATED 状态下都存在，独立维护身份与存储不变量。它不实现 TrackerItem，避免把没有完整业务内容的索引当作业务对象。

```java
public enum InstantiationState { VIRTUAL, INSTANTIATED }

public record ItemSourceRef(Long sourceLocalId, int sourceRevision) {
    public ItemSourceRef {
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("sourceRevision must be positive");
        }
    }
}

// 位于 sk-workingset 内部包；常规业务调用方不依赖此类。
public final class LightTrackerItem {
    private final TrackerItemIdentity identity;
    private final String itemType;
    private ItemSourceRef source;
    private InstantiationState instantiationState;
    private DeletionState deletionState;
    private final long version; // 加载时的乐观锁版本；保存后重新加载。

    private LightTrackerItem(TrackerItemIdentity identity, String itemType,
                             ItemSourceRef source, InstantiationState state,
                             DeletionState deletionState, long version) {
        this.identity = Objects.requireNonNull(identity);
        this.itemType = Objects.requireNonNull(itemType);
        this.source = Objects.requireNonNull(source);
        this.instantiationState = Objects.requireNonNull(state);
        this.deletionState = Objects.requireNonNull(deletionState);
        this.version = version;
        if (state == InstantiationState.VIRTUAL) {
            if (identity.commonId().equals(identity.localId())
                    || source.sourceLocalId().equals(identity.localId())
                    || deletionState != DeletionState.ACTIVE) {
                throw new IllegalArgumentException("Invalid VIRTUAL item");
            }
        } else if (!source.sourceLocalId().equals(identity.localId())) {
            throw new IllegalArgumentException("Local content must refer to itself");
        }
    }

    public static LightTrackerItem reconstitute(
            TrackerItemIdentity identity, String itemType, ItemSourceRef source,
            InstantiationState state, DeletionState deletionState, long version) {
        return new LightTrackerItem(identity, itemType, source, state,
                                    deletionState, version);
    }

    public void markInstantiated(int localRevision) {
        if (!isVirtual()) {
            throw new IllegalStateException("Item is already instantiated");
        }
        source = new ItemSourceRef(getLocalId(), localRevision);
        instantiationState = InstantiationState.INSTANTIATED;
    }

    public static LightTrackerItem provisionOriginal(
            TrackerItemIdentity identity, String itemType, int localRevision) {
        if (!identity.commonId().equals(identity.localId())) {
            throw new IllegalArgumentException("Original item identities must match");
        }
        return reconstitute(identity, itemType,
                new ItemSourceRef(identity.localId(), localRevision),
                InstantiationState.INSTANTIATED, DeletionState.ACTIVE, 0);
    }

    public static LightTrackerItem provisionVirtual(
            TrackerItemIdentity identity, String itemType, ItemSourceRef source) {
        return reconstitute(identity, itemType, source,
                InstantiationState.VIRTUAL, DeletionState.ACTIVE, 0);
    }

    public ItemSourceRef resolveContentRef() { return source; }

    public void refreshLocalRevision(int revision) {
        if (!isInstantiated()) {
            throw new IllegalStateException("Local revision requires instantiated item");
        }
        source = new ItemSourceRef(getLocalId(), revision);
    }

    public void markDeleted() {
        if (!isInstantiated() || deletionState != DeletionState.ACTIVE) {
            throw new IllegalStateException("Invalid delete transition");
        }
        deletionState = DeletionState.DELETED;
    }

    public void markRestored() {
        if (!isInstantiated() || deletionState != DeletionState.DELETED) {
            throw new IllegalStateException("Invalid restore transition");
        }
        deletionState = DeletionState.ACTIVE;
    }

    public void synchronizeSaved(SavedTrackerItem saved) {
        refreshLocalRevision(saved.localRevision());
        if (deletionState != saved.deletionState()) {
            if (saved.deletionState() == DeletionState.DELETED) {
                markDeleted();
            } else {
                markRestored();
            }
        }
    }

    public void requireDelete2Allowed() {
        if (!isInstantiated() || deletionState != DeletionState.DELETED) {
            throw new IllegalStateException("delete2 requires instantiated recycle-bin item");
        }
    }

    public Long getTrackerId() { return identity.trackerId(); }
    public Long getCommonId() { return identity.commonId(); }
    public Long getLocalId() { return identity.localId(); }
    public String getItemType() { return itemType; }
    public ItemSourceRef getSource() { return source; }
    public long getVersion() { return version; }
    public boolean isVirtual() { return instantiationState == InstantiationState.VIRTUAL; }
    public boolean isInstantiated() {
        return instantiationState == InstantiationState.INSTANTIATED;
    }
}
```

索引上的 DeletionState 是实际业务生命周期的同步摘要，必须在保存实际对象的同一事务中更新。实体本身负责合法状态转换，写协调器还要校验操作意图与原状态匹配；不能把 synchronizeSaved 当作任意改状态的公共命令。

## 6. 强类型批量查询

### 6.1 公开输入与内部读取上下文

```java
public record TrackerItemLocator(Long workingSetId, Long commonId) {
    public TrackerItemLocator {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(commonId, "commonId");
    }
}

// 内部上下文：一次加载 Binding、Tracker 归属、索引与权限。
// 集合覆盖请求的全部唯一 CommonId；只含 READY、ACTIVE 且可见条目。
public record TrackerItemReadContext(
        Map<Long, LightTrackerItem> byCommonId,
        Map<Long, TrackerItemTarget> targetsByLocalId,
        Map<Long, SourceReadRequest> sourcesByLocalId
) {
    public TrackerItemTarget targetFor(LightTrackerItem item) {
        return Objects.requireNonNull(targetsByLocalId.get(item.getLocalId()));
    }

    public SourceReadRequest sourceFor(LightTrackerItem item) {
        return Objects.requireNonNull(sourcesByLocalId.get(item.getLocalId()));
    }
}

public interface TrackerItemReadContextLoader {
    TrackerItemReadContext loadVisible(Long workingSetId, List<Long> commonIds);
    boolean unchanged(TrackerItemReadContext context);
}

public final class ContentUnavailableException extends RuntimeException {
    public ContentUnavailableException() {
        super("Content is unavailable at the selected source");
    }
}
```

SourceReadRequest 由上下文 Loader 根据固定来源与快照解析；内容端口不自行补查 Binding。来源物理实例必须已经存在，不能将 VIRTUAL 父条目的 LocalId 直接当作可读取的物理行。非版次对象的 capturedAt 必须是有效内容的确定切点，不能无条件用当前时间。

### 6.2 Resolver

```java
@RequiredArgsConstructor
public final class TrackerItemResolver {
    private static final int MAX_READ_ATTEMPTS = 3;

    private final TrackerItemReadContextLoader contextLoader;
    private final TrackerItemTypeRegistry registry;

    public <T extends TrackerItem> T resolve(
            TrackerItemLocator locator, Class<T> expectedClass) {
        return resolveAll(locator.workingSetId(),
                List.of(locator.commonId()), expectedClass).get(0);
    }

    public <T extends TrackerItem> List<T> resolveAll(
            Long workingSetId, List<Long> commonIds, Class<T> expectedClass) {
        if (commonIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = List.copyOf(commonIds);
        for (int attempt = 0; attempt < MAX_READ_ATTEMPTS; attempt++) {
            TrackerItemReadContext context = contextLoader.loadVisible(
                    workingSetId, ids.stream().distinct().toList());
            Map<String, List<LightTrackerItem>> groups =
                    context.byCommonId().values().stream().collect(
                            Collectors.groupingBy(LightTrackerItem::getItemType));
            Map<Long, T> resolved = new HashMap<>();
            try {
                for (var group : groups.entrySet()) {
                    var contentPort = registry.require(group.getKey(), expectedClass).contentPort();
                    resolveGroup(context, group.getValue(), contentPort, resolved);
                }
            } catch (ContentUnavailableException failure) {
                if (!contextLoader.unchanged(context)) {
                    continue;
                }
                throw failure;
            }
            if (contextLoader.unchanged(context)) {
                // 保留请求顺序和重复项；缺失不能静默过滤。
                return ids.stream().map(id ->
                        Objects.requireNonNull(resolved.get(id))).toList();
            }
        }
        throw new IllegalStateException("Concurrent item change; retry the query");
    }

    private <T extends TrackerItem> void resolveGroup(
            TrackerItemReadContext context, List<LightTrackerItem> group,
            TrackerItemContentPort<T> contentPort, Map<Long, T> resolved) {
        List<Long> localIds = group.stream().filter(LightTrackerItem::isInstantiated)
                .map(LightTrackerItem::getLocalId).distinct().toList();
        List<SourceReadRequest> sources = group.stream()
                .filter(LightTrackerItem::isVirtual).map(context::sourceFor)
                .distinct().toList();
        Map<Long, T> locals = localIds.isEmpty()
                ? Map.of() : contentPort.loadLocalBatch(localIds);
        Map<SourceReadRequest, TrackerItemSnapshot> snapshots = sources.isEmpty()
                ? Map.of() : contentPort.loadSourceBatch(sources);
        for (var light : group) {
            T item = light.isInstantiated()
                    ? requireContent(locals, light.getLocalId())
                    : contentPort.resolve(context.targetFor(light),
                                      requireContent(snapshots, context.sourceFor(light)));
            validateIdentity(light, item);
            if (item.isInstantiated() != light.isInstantiated()) {
                throw new IllegalStateException("ContentPort returned inconsistent loaded state");
            }
            if (item.getDeletionState() != DeletionState.ACTIVE) {
                throw new ContentUnavailableException();
            }
            resolved.put(light.getCommonId(), item);
        }
    }

    static void validateIdentity(LightTrackerItem light, TrackerItem item) {
        if (!light.getTrackerId().equals(item.getTrackerId())
                || !light.getCommonId().equals(item.getCommonId())
                || !light.getLocalId().equals(item.getLocalId())
                || !light.getItemType().equals(item.getTrackerItemType())) {
            throw new IllegalStateException("ContentPort returned a different item identity");
        }
    }

    private static <K, V> V requireContent(Map<K, V> values, K key) {
        V value = values.get(key);
        if (value == null) {
            throw new ContentUnavailableException();
        }
        return value;
    }
}
```

类型适配器返回对象还须按已加载目标上下文校验项目/产品空间归属；示例身份检查展示了四个通用字段，类型特有归属校验由 Store/适配器 合同覆盖。读上下文 unchanged 必须同时覆盖 Binding 与轻量索引版本；版本检测不会替代快照历史保留。

## 7. 写命令自动准备 COW

### 7.1 写协议端口

```java
public enum WriteIntent { CHANGE, INSTANTIATE, RESTORE, DELETE2 }

public interface MutationScope {
    List<LightTrackerItem> roots();
    TrackerItemTarget targetFor(LightTrackerItem item);
    SourceReadRequest sourceFor(LightTrackerItem item);
    void verifyBeforeWrite();
}

public interface LineageWriteGuard {
    // 校验权限/类型/生命周期，按 CommonId 固定顺序取写保护。
    // 绑定当前 Management 写事务，提交或回滚后释放；不能方法返回即解锁。
    MutationScope acquireAndEnlist(
            Long workingSetId, List<Long> commonIds,
            Class<? extends TrackerItem> expectedClass, WriteIntent intent);
}

public record InstantiationClosure(List<LightTrackerItem> topologicalOrder) {}

public interface InstantiationPlanner {
    // 纯领域计算的实现消费已加载派生图；不在节点循环中隐式查库。
    InstantiationClosure plan(MutationScope scope);
}

public interface SourceSnapshotLoader {
    // 按类型批量取全部 VIRTUAL 节点的来源；重复来源去重。
    Map<Long, TrackerItemSnapshot> loadByTargetLocalId(
            MutationScope scope, List<LightTrackerItem> virtualItems);
}

public interface MutationContextLoader {
    // 闭包准备后显式重载目标索引版本并更新内部命令上下文。
    void refreshRoots(MutationScope scope);
}

public interface LocalTrackerItemLoader {
    // 仅加载已有物理实例，按类型批量；支持 scope 中合法的回收站目标。
    <T extends TrackerItem> List<T> loadRoots(
            MutationScope scope, Class<T> expectedClass);
}

public interface LightTrackerItemRepository {
    // 内容写入与索引 CAS 使用当前事务；同时校验 scope 的 fencing/lineageVersion。
    void saveCas(LightTrackerItem item, long expectedVersion, MutationScope scope);
    void deleteCas(LightTrackerItem item, long expectedVersion, MutationScope scope);
}

public interface ItemOutbox {
    void instantiated(LightTrackerItem item);
    void saved(LightTrackerItem item, SavedTrackerItem saved);
    void physicallyDeleted(TrackerItemIdentity identity, String itemType);
}
```

这些是需要实现的基础设施协议，不是空实现。Guard 需要检查并冻结当前 Binding、读取派生图、阻止同血缘并发派生；Planner 的闭包必须覆盖当前节点及后代，SourceSnapshotLoader/LocalTrackerItemLoader 必须真正批量读取。实际写入以同一事务内的 fencing/CAS 兜底，事务失败回滚业务物理行和轻量索引。

### 7.2 实例化协调器

```java
@RequiredArgsConstructor
public final class LineageInstantiationService {
    private final InstantiationPlanner planner;
    private final SourceSnapshotLoader sourceLoader;
    private final TrackerItemTypeRegistry registry;
    private final LightTrackerItemRepository lightRepository;
    private final ItemOutbox outbox;

    public void ensureReady(MutationScope scope) {
        instantiateItems(scope, planner.plan(scope).topologicalOrder());
    }

    private void instantiateItems(MutationScope scope, List<LightTrackerItem> items) {
        List<LightTrackerItem> virtuals = items.stream()
                .filter(LightTrackerItem::isVirtual).toList();
        Map<Long, TrackerItemSnapshot> snapshots =
                sourceLoader.loadByTargetLocalId(scope, virtuals);
        for (LightTrackerItem light : virtuals) {
            scope.verifyBeforeWrite();
            TrackerItem item = registry.require(light.getItemType()).contentPort()
                    .instantiateLocal(scope.targetFor(light),
                            Objects.requireNonNull(snapshots.get(light.getLocalId())));
            TrackerItemResolver.validateIdentity(light, item);
            if (!item.isInstantiated() || item.getDeletionState() != DeletionState.ACTIVE) {
                throw new IllegalStateException("Physical copy must be active and instantiated");
            }
        }
        synchronizeCreated(scope, virtuals);
    }

    private void synchronizeCreated(MutationScope scope, List<LightTrackerItem> virtuals) {
        var groups = virtuals.stream().collect(
                Collectors.groupingBy(LightTrackerItem::getItemType));
        for (var group : groups.entrySet()) {
            List<Long> ids = group.getValue().stream().map(LightTrackerItem::getLocalId).toList();
            Map<Long, SavedTrackerItem> states = registry.require(group.getKey())
                    .contentPort().loadSavedStateBatch(ids);
            for (LightTrackerItem light : group.getValue()) {
                scope.verifyBeforeWrite();
                SavedTrackerItem saved = Objects.requireNonNull(states.get(light.getLocalId()));
                if (saved.deletionState() != DeletionState.ACTIVE) {
                    throw new IllegalStateException("Created content must be active");
                }
                light.markInstantiated(saved.localRevision());
                lightRepository.saveCas(light, light.getVersion(), scope);
                outbox.instantiated(light);
            }
        }
    }
}
```

此 demo 选择一个有界闭包在当前事务内准备。闭包很大时应使用可恢复准备流程和多个独立 Management 事务，准备完成后再执行业务命令；当前例子没有实现该长任务模式。

### 7.3 业务写入协调器

```java
@RequiredArgsConstructor
public final class TrackerItemMutationService {
    private final LineageWriteGuard writeGuard;
    private final LineageInstantiationService instantiation;
    private final LocalTrackerItemLoader localLoader;
    private final MutationContextLoader contextLoader;
    private final TrackerItemTypeRegistry registry;
    private final LightTrackerItemRepository lightRepository;
    private final ItemOutbox outbox;

    public <T extends TrackerItem> void executeAll(
            Long workingSetId, List<Long> commonIds, Class<T> expectedClass,
            Consumer<List<T>> businessBehavior, WriteIntent intent) {
        if (intent != WriteIntent.CHANGE && intent != WriteIntent.RESTORE) {
            throw new IllegalArgumentException("Mutation accepts CHANGE or RESTORE only");
        }
        if (commonIds.isEmpty()) {
            return;
        }
        List<Long> uniqueIds = commonIds.stream().distinct().sorted().toList();
        MutationScope scope = writeGuard.acquireAndEnlist(
                workingSetId, uniqueIds, expectedClass, intent);
        if (intent == WriteIntent.CHANGE) {
            instantiation.ensureReady(scope);
        }
        // 显式重载实例化后的索引版本，不能复用准备前版本。
        contextLoader.refreshRoots(scope);
        List<T> items = localLoader.loadRoots(scope, expectedClass);
        businessBehavior.accept(List.copyOf(items));
        Map<Long, T> byLocalId = items.stream().collect(
                Collectors.toMap(TrackerItem::getLocalId, Function.identity()));
        // roots() 在此处返回当前事务重载后的轻量条目。
        for (LightTrackerItem light : scope.roots()) {
            scope.verifyBeforeWrite();
            T item = Objects.requireNonNull(byLocalId.get(light.getLocalId()));
            TrackerItemResolver.validateIdentity(light, item);
            SavedTrackerItem saved = registry.require(
                    light.getItemType(), expectedClass).contentPort().save(item);
            light.synchronizeSaved(saved);
            lightRepository.saveCas(light, light.getVersion(), scope);
            outbox.saved(light, saved);
        }
        // 不手动 release：由 Guard 注册的事务完成回调负责。
    }
}
```

回调仅执行当前用例内的实体行为，不做远程写入、不把实体保存到另一个 Repository、不逃逸当前命令作用域。小批量的业务修改原子提交，重复 CommonId 只处理一次；锁按稳定顺序获取，传入回调的列表顺序在本例中为 CommonId 顺序。真实系统应设定批量上限。

delete 使用同一 CHANGE 路径先准备闭包，再调用 TrackerItem.delete；undelete 使用 RESTORE，只允许已实例化回收站对象，并调用 undelete。Restore 后内容版次和删除摘要也需要同步。DELETE2 不通过普通 save，由下节专用用例处理。

### 7.4 TrackerItemInstantiator 的一种实现

Instantiator 是 SK 公开协调契约，由 SK 的 sdkImpl 实现，不再由具体 BC 实现。以工作集定位器或 CommonId 集合接受请求，不接收 detached 业务对象。INSTANTIATE 意图只允许 ACTIVE 目标，固定顺序获取血缘保护，使用与 CHANGE 相同的闭包规划；不允许业务回调编辑内容。空列表直接返回，重复目标只准备一次，结果按请求顺序保留重复项。

```java
@Service
@RequiredArgsConstructor
public final class WorkingSetTrackerItemInstantiator implements TrackerItemInstantiator {
    private final LineageWriteGuard guard;
    private final LineageInstantiationService instantiation;
    private final MutationContextLoader contextLoader;
    private final LocalTrackerItemLoader localLoader;

    @Override public TrackerItem instantiate(TrackerItemLocator locator) {
        return instantiate(locator, TrackerItem.class);
    }

    @Override public <T extends TrackerItem> T instantiate(
            TrackerItemLocator locator, Class<T> type) {
        return instantiateAll(locator.workingSetId(), List.of(locator.commonId()), type).get(0);
    }

    @Override public <T extends TrackerItem> List<T> instantiateAll(
            Long workingSetId, List<Long> commonIds, Class<T> type) {
        List<Long> requested = List.copyOf(commonIds);
        if (requested.isEmpty()) {
            return List.of();
        }
        List<Long> uniqueIds = requested.stream().distinct().sorted().toList();
        MutationScope scope = guard.acquireAndEnlist(
                workingSetId, uniqueIds, type, WriteIntent.INSTANTIATE);
        // 即使根已实例化，也必须检查和准备尚为 VIRTUAL 的派生后代。
        instantiation.ensureReady(scope);
        contextLoader.refreshRoots(scope);
        scope.verifyBeforeWrite();
        Map<Long, T> byCommonId = new HashMap<>();
        for (T item : localLoader.loadRoots(scope, type)) {
            if (!item.isInstantiated()) {
                throw new IllegalStateException("Reloaded item must be instantiated");
            }
            byCommonId.put(item.getCommonId(), item);
        }
        for (LightTrackerItem root : scope.roots()) {
            TrackerItemResolver.validateIdentity(root,
                    Objects.requireNonNull(byCommonId.get(root.getCommonId())));
        }
        return requested.stream().map(id -> Objects.requireNonNull(byCommonId.get(id))).toList();
        // Guard 注册事务完成回调释放保护；不能在这里提前 release。
    }
}
```

准备范围是请求目标及其 Tracker 派生子树中需要独立内容的 VIRTUAL 条目，已实例化节点不重复复制，来源祖先只读取并保护，不自动实例化整族。返回值只包括请求目标，不暴露闭包。实例化不保存任何 detached 对象的编辑，不执行业务回调；后续 update/delete 仍重新校验、重新规划，不能依赖 isInstantiated() 跳过保护。

### 7.5 Management 调用示例

```java
@Service
@RequiredArgsConstructor
public class TrackerItemInstantiationManagement {
    private final TrackerItemInstantiator instantiator;

    @Transactional(rollbackFor = Exception.class)
    public Requirement instantiateRequirement(TrackerItemLocator locator) {
        return instantiator.instantiate(locator, Requirement.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Requirement> instantiateRequirements(Long workingSetId, List<Long> commonIds) {
        return instantiator.instantiateAll(workingSetId, commonIds, Requirement.class);
    }
}
```

事务只在 biz Management 声明。SDK 定义 Instantiator，SK sdkImpl 提供实现；Management 不依赖内部实现、索引或内容端口。普通 mutate/mutateAll 和 delete 自动准备相同 COW 闭包，无须先显式实例化；显式能力适用于确实需要提前建立独立本地内容的用例。

## 8. delete2 的实际清理

```java
public interface Delete2Specification {
    void requireAllowed(BusinessObject item);
}

public interface ObjectRelationRepository {
    // 清理该 LocalId 拥有或作为物理端点的关系；
    // 不按 CommonId 清空其他变体仍在使用的规范引用。
    void deleteForLocalInstance(Long localId);
}

@RequiredArgsConstructor
public final class BusinessObjectDeletionService {
    private final LineageWriteGuard guard;
    private final LocalTrackerItemLoader loader;
    private final TrackerItemTypeRegistry registry;
    private final Delete2Specification specification;
    private final ObjectRelationRepository relations;
    private final LightTrackerItemRepository lightRepository;
    private final ItemOutbox outbox;

    public void delete2(TrackerItemLocator locator) {
        MutationScope scope = guard.acquireAndEnlist(locator.workingSetId(),
                List.of(locator.commonId()), TrackerItem.class, WriteIntent.DELETE2);
        LightTrackerItem light = scope.roots().get(0);
        light.requireDelete2Allowed();
        TrackerItem item = loader.loadRoots(scope, TrackerItem.class).get(0);
        specification.requireAllowed(item);
        item.delete2();
        scope.verifyBeforeWrite();
        TrackerItemIdentity identity = new TrackerItemIdentity(
                item.getTrackerId(), item.getCommonId(), item.getLocalId());

        relations.deleteForLocalInstance(item.getLocalId());
        registry.require(item.getTrackerItemType()).contentPort().deletePhysical(item.getLocalId());
        lightRepository.deleteCas(light, light.getVersion(), scope);
        outbox.physicallyDeleted(identity, item.getTrackerItemType());
    }
}
```

Specification 需处理 Snapshot 保留、历史来源保护和类型从属数据。实体、轻量索引、版次、关系及 Outbox 在同一写事务中提交；真实附件/外部存储需通过可靠清理流程处理。delete2 成功不留索引或删除占位，原 ID 不复用。跨 CommonId 的派生族逻辑引用按各工作集上下文重新判断，不承诺一个变体删除后整个族的引用都消失。

## 9. 非 WorkingSet/Tracker 管控对象 Part

Part 实现 BusinessObject，具有 CommonId/LocalId 和删除生命周期，但没有 TrackerId、LightTrackerItem 或工作集定位语义。

```java
public final class Part implements BusinessObject {
    private final Long commonId;
    private final Long localId;
    private String number;
    private String name;
    private DeletionState deletionState;

    private Part(Long commonId, Long localId, String number, String name,
                 DeletionState deletionState) {
        this.commonId = Objects.requireNonNull(commonId);
        this.localId = Objects.requireNonNull(localId);
        this.number = Objects.requireNonNull(number);
        this.name = Objects.requireNonNull(name);
        this.deletionState = Objects.requireNonNull(deletionState);
    }

    public static Part create(Long id, String number, String name) {
        return new Part(id, id, number, name, DeletionState.ACTIVE);
    }

    public static Part reconstitute(Long commonId, Long localId, String number,
                                    String name, DeletionState state) {
        return new Part(commonId, localId, number, name, state);
    }

    public void revise(String number, String name) {
        if (deletionState != DeletionState.ACTIVE) {
            throw new IllegalStateException("Part is deleted");
        }
        this.number = Objects.requireNonNull(number);
        this.name = Objects.requireNonNull(name);
    }

    @Override public Long getCommonId() { return commonId; }
    @Override public Long getLocalId() { return localId; }
    @Override public DeletionState getDeletionState() { return deletionState; }
    @Override public BusinessObjectData getBusinessData() {
        return new BusinessObjectData(Map.of("number", number, "name", name));
    }

    @Override public void delete() {
        if (deletionState != DeletionState.ACTIVE) {
            throw new IllegalStateException("Part is already deleted");
        }
        deletionState = DeletionState.DELETED;
    }

    @Override public void undelete() {
        requireDeleted();
        deletionState = DeletionState.ACTIVE;
    }

    @Override public void delete2() { requireDeleted(); }

    private void requireDeleted() {
        if (deletionState != DeletionState.DELETED) {
            throw new IllegalStateException("Part is not in recycle bin");
        }
    }
}

public interface IPartService {
    Part getPart(Long localId);
    void save(Part part);
    // 在 Part Management 的事务中完成该对象的实体/从属数据/关系清理。
    void delete2(Long localId);
}
```

普通 Part 的修改是 getPart→Part.revise→save；delete2 由 Part 生命周期协调器执行相同的回收站前置校验和实际清理。它不进入 LineageWriteGuard 或实例化闭包。

普通 Part 的基础设施适配同样由 sdkImpl 实现。下面用内部 Store 表达完整加载与删除，不给普通 Part 引入工作集扩展契约：

```java
public interface PartStore {
    Part requireDetached(Long localId);
    void save(Part part);
    void deleteOwnedData(Long localId);
}

@Service
@RequiredArgsConstructor
public final class PartServiceImpl implements IPartService {
    private final PartStore store;
    private final Delete2Specification specification;
    private final ObjectRelationRepository relations;

    @Override public Part getPart(Long localId) {
        return store.requireDetached(localId);
    }

    @Override public void save(Part part) {
        store.save(part);
    }

    @Override public void delete2(Long localId) {
        Part part = store.requireDetached(localId);
        specification.requireAllowed(part);
        part.delete2();
        relations.deleteForLocalInstance(localId);
        store.deleteOwnedData(localId);
    }
}
```

真实 Part 保存/删除仍须校验版本并在 Management 事务内执行，其删除事件与数据同步提交；这里没有重复实现通用事件基础设施。

## 10. 统一 SDK 门面与业务应用编排

### 10.1 SK SDK 门面

```java
public interface IWorkingSetItemService {
    TrackerItem resolve(TrackerItemLocator locator);

    <T extends TrackerItem> T resolve(TrackerItemLocator locator, Class<T> type);

    <T extends TrackerItem> List<T> resolveAll(
            Long workingSetId, List<Long> commonIds, Class<T> type);

    <T extends TrackerItem> void mutate(
            TrackerItemLocator locator, Class<T> type, Consumer<T> behavior);

    <T extends TrackerItem> void mutateAll(
            Long workingSetId, List<Long> commonIds, Class<T> type,
            Consumer<List<T>> behavior);

    void delete(TrackerItemLocator locator);
    void undelete(TrackerItemLocator locator);
    void delete2(TrackerItemLocator locator);
}

@Service
@RequiredArgsConstructor
public final class WorkingSetItemServiceImpl implements IWorkingSetItemService {
    private final TrackerItemResolver resolver;
    private final TrackerItemMutationService mutation;
    private final BusinessObjectDeletionService deletion;

    @Override public TrackerItem resolve(TrackerItemLocator locator) {
        return resolve(locator, TrackerItem.class);
    }

    @Override public <T extends TrackerItem> T resolve(
            TrackerItemLocator locator, Class<T> type) {
        return resolver.resolve(locator, type);
    }

    @Override public <T extends TrackerItem> List<T> resolveAll(
            Long workingSetId, List<Long> commonIds, Class<T> type) {
        return resolver.resolveAll(workingSetId, commonIds, type);
    }

    @Override public <T extends TrackerItem> void mutate(
            TrackerItemLocator locator, Class<T> type, Consumer<T> behavior) {
        mutateAll(locator.workingSetId(), List.of(locator.commonId()), type,
                  items -> behavior.accept(items.get(0)));
    }

    @Override public <T extends TrackerItem> void mutateAll(
            Long workingSetId, List<Long> commonIds, Class<T> type,
            Consumer<List<T>> behavior) {
        mutation.executeAll(workingSetId, commonIds, type, behavior, WriteIntent.CHANGE);
    }

    @Override public void delete(TrackerItemLocator locator) {
        mutate(locator, TrackerItem.class, TrackerItem::delete);
    }

    @Override public void undelete(TrackerItemLocator locator) {
        mutation.executeAll(locator.workingSetId(), List.of(locator.commonId()),
                TrackerItem.class, items -> items.get(0).undelete(), WriteIntent.RESTORE);
    }

    @Override public void delete2(TrackerItemLocator locator) {
        deletion.delete2(locator);
    }
}
```

### 10.2 Part、Requirement、Bug 用例

```java
public record RevisePartCommand(Long localId, String number, String name) {}
public record ReviseRequirementCommand(
        Long workingSetId, List<Long> commonIds, String title, String description) {}
public record ResolveBugCommand(
        Long workingSetId, Long commonId, String resolution) {}

// 是业务用例的组合结果，字段直接使用业务对象。
public record EngineeringContext(
        Part part, List<Requirement> requirements, List<Bug> bugs) {}

@Service
@RequiredArgsConstructor
public class EngineeringObjectManagement {
    private final IPartService parts;
    private final IWorkingSetItemService items;

    @Transactional(rollbackFor = Exception.class)
    public void revisePart(RevisePartCommand command) {
        Part part = parts.getPart(command.localId());
        part.revise(command.number(), command.name());
        parts.save(part);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviseRequirements(ReviseRequirementCommand command) {
        items.mutateAll(command.workingSetId(), command.commonIds(), Requirement.class,
                requirements -> requirements.forEach(requirement ->
                        requirement.revise(command.title(), command.description())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void resolveBug(ResolveBugCommand command) {
        items.mutate(new TrackerItemLocator(command.workingSetId(), command.commonId()),
                Bug.class, bug -> bug.resolve(command.resolution()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRequirement(TrackerItemLocator locator) { items.delete(locator); }

    @Transactional(rollbackFor = Exception.class)
    public void restoreRequirement(TrackerItemLocator locator) { items.undelete(locator); }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRequirement2(TrackerItemLocator locator) { items.delete2(locator); }

    @Transactional(rollbackFor = Exception.class)
    public void deletePart2(Long localId) { parts.delete2(localId); }

    // 普通读用例不声明事务；底层读一致性由 SK 解析协议保证。
    public EngineeringContext loadContext(Long partLocalId, Long workingSetId,
                                           List<Long> requirementIds, List<Long> bugIds) {
        return new EngineeringContext(parts.getPart(partLocalId),
                items.resolveAll(workingSetId, requirementIds, Requirement.class),
                items.resolveAll(workingSetId, bugIds, Bug.class));
    }
}
```

各业务用例保持自己的领域规则。Management 只通过 SDK 访问基础设施，sdk/sdkImpl 不增加 @Transactional；Store 实现在 sdkImpl 使用 DataObject Framework，示例主键为 Long。跨对象必须原子执行的真实业务不变量应单独确定，不能因使用同一门面就默认把整个 Tracker 放进一个事务。

引用的完整语义、关系合并与清理边界统一见 [09 规范引用](./09-merge-domain-design.md#reference-design)；比对合并策略及执行见 [09 执行协议](./09-merge-domain-design.md#merge-execution)。本 demo 不重复实现这些专项能力。

## 11. 实现前必须兑现的契约

| 契约 | 本 demo 的表达 |
|------|---------------|
| 对外统一类型 | resolve/resolveAll 返回 TrackerItem 或 T/List<T> |
| 内部轻量表示 | LightTrackerItem 全状态存在，与业务对象组合 |
| 公开实例化能力 | SK Instantiator 统一准备目标派生闭包，直接返回请求目标 T/List<T> |
| 只读实例化标记 | isInstantiated 为加载事实，无 setter、不独立持久化、不授予写权限 |
| 查询没有写副作用 | TrackerItemContentPort.resolve 只构造目标上下文业务对象 |
| 完整强类型列表 | 批量 Loader、严格完整性校验、请求顺序与重复项保留 |
| 写入保护 | Guard 持续到事务完成，真实写入 fencing/CAS，准备后重载版本 |
| 状态同步 | save 返回本地版次与删除状态，同事务同步轻量索引 |
| 历史快照 | 来源内容保留并可按确定 cut 回读；revision=1 不替代历史 |
| 普通业务对象 | Part 不进入工作集派生/COW 协议 |
| 实际未完成部分 | Guard、Loader、Planner、Store、Repository、Outbox 的基础设施实现与集成验证 |

完整实例化还需复制合法从属数据、校正权限/项目/产品空间归属、处理业务唯一字段与版本策略；此参考示例没有宣称这些基础设施已经落地。支持普通 load→改对象→save 的自动 Unit of Work 可以以后增加，但必须满足相同写保护与并发条件。
