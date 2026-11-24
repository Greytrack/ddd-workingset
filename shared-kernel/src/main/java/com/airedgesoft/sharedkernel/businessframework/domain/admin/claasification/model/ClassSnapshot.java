package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute.AttributeSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.table.TableSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.ClassificationFacts;
import lombok.Data;

import java.util.*;

/**
 * 分类快照（替代 v3 的 {@code IInnerAirClass}/{@code AirSubClass}/{@code AirBaseClass}/{@code BaseClassNode}）。
 *
 * <p>
 * 字段面按判权实际读取收窄；v3 的对象模型推导（中类/子类合并、变更控制继承）由桥接侧算好再填入。 方法名与原件一致，使迁入代码的调用点只换类型。
 *
 * <p>
 * <b>过渡期定位</b>（用户拍板）：v4 分类元数据未重建前，本快照即分类元数据的过渡模型——同时承担分类视图（{@link ClassificationFacts}）
 * 与落库父类推导（{@link #getBaseClassId()}）的职责；v4 元数据重建后随 bridge 退役。
 */
@Data
public class ClassSnapshot implements ClassificationFacts {

  /** Builds the classification metadata needed by the Tracker catalog. */
  public static ClassSnapshot forTracker(Long id, boolean shared, String name, Long baseClassId, Long classId) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("classificationId must be positive");
    }
    ClassSnapshot snapshot = new ClassSnapshot();
    snapshot.setId(id);
    snapshot.setName(name);
    snapshot.setClassId(classId);
    snapshot.setBaseClassId(baseClassId);
    snapshot.setShared(shared);
    return snapshot;
  }

  /**
   * 分类节点 ID
   */
  private Long id;

  /**
   * 分类 API ID
   */
  private String apiId;

  /**
   * 显示名（v3 Node#getName）
   */
  private String name;

  /**
   * 节点类型
   */
  private Long type;

  /**
   * 分类主表名
   */
  private String mainTableName;

  /**
   * 分类主表物理表名
   */
  private String dbTableName;

  /**
   * 是否基础类
   */
  private boolean baseClassFlag;

  /**
   * 基础类 ID
   */
  private Long baseClassId;

  /**
   * 父分类 ID
   */
  private Long parentClassId;

  /**
   * 基础类（自引用）
   */
  private ClassSnapshot baseClass;

  /**
   * 父分类（自引用）
   */
  private ClassSnapshot parentClass;

  /**
   * 子分类（含中类与子类）
   */
  private List<ClassSnapshot> subclasses;

  /**
   * 子类集合（仅基础类有值）
   */
  private List<ClassSnapshot> classes;

  /**
   * 全部可用属性
   */
  private List<AttributeSnapshot> attributes;

  /**
   * 全部属性（含已停用）
   */
  private List<AttributeSnapshot> attributesWithDisabled;

  /**
   * 主对象属性映射：属性 API ID → 属性快照
   */
  private Map<String, AttributeSnapshot> mainObjectAttributes;

  /**
   * 属性 ID → 所属页签
   */
  private Map<Long, TableSnapshot> tableDescByAttrId;

  /**
   * 页签描述集合
   */
  private List<TableSnapshot> tableDescriptors;

  /**
   * 中类 ID
   */
  private Long classId;

  /**
   * 工作区视图表名
   */
  private String mainWsViewTableName;

  /**
   * 是否中类
   */
  private boolean classNode;

  /**
   * 是否子类
   */
  private boolean subclass;

  /**
   * 是否启用变更控制
   */
  private boolean changeControl;

  /**
   * 是否 Tracker 扩展类（v3 IAirClass#isTrackerExtensionClass）
   */
  private boolean trackerExtension;

  /**
   * 全局 Tracker 分类 ID（v3 IAirClass#getGlobalTracker）
   */
  private Long globalTrackerId;

  // —— 分类视图（过渡期：分类元数据驱动的装配 / 校验路径读取） ——

  /**
   * 分类是否启用（v3 {@code SubClass#getEnabled} 口径：未置位视为启用）
   */
  private Boolean enabled;

  /**
   * 是否是共享数据
   */
  private boolean shared;

  /**
   * 子项页签是否启用（v3 {@code TableSubItems} 页签 enabled）
   */
  private boolean subItemsTabEnabled;

  // —— 与 v3 同名方法，保持调用点零改动 ——

  public String getAPIID() {
    return apiId;
  }

  public boolean isBaseClass() {
    return baseClassFlag;
  }

  public Long getClassId() {
    return classId;
  }

  public String getMainWsViewTableName() {
    return mainWsViewTableName;
  }

  public boolean isShared() {
    return shared;
  }

  public boolean isClass() {
    return classNode;
  }

  public boolean isSubclass() {
    return subclass;
  }

  public List<ClassSnapshot> getClasses() {
    return classes;
  }

  public List<AttributeSnapshot> getAllClassAttributes() {
    return attributes;
  }

  public List<AttributeSnapshot> getAllClassAttributesWithDisabled() {
    return attributesWithDisabled;
  }

  public Map<String, AttributeSnapshot> getMainObjectAttrMap() {
    return mainObjectAttributes;
  }

  public TableSnapshot getTableDescByAttrId(Long attributeId) {
    return tableDescByAttrId == null ? null : tableDescByAttrId.get(attributeId);
  }

  public boolean yesOrNoChangeControl() {
    return changeControl;
  }

  /**
   * v3 语义：按子类型取孙类；权限侧仅取 ID 集合使用
   */
  public List<ClassSnapshot> getGrandchildsByChildType(Integer childType) {
    return subclasses == null ? List.of() : subclasses;
  }

  public List<TableSnapshot> getTableDescriptors() {
    return tableDescriptors;
  }

  public TableSnapshot getTableDescriptorByTabId(Long tabId) {
    if (tableDescriptors == null || tabId == null) {
      return null;
    }
    return tableDescriptors.stream().filter(tab -> tabId.equals(tab.getId())).findFirst().orElse(null);
  }

  /**
   * v3 兼容重载：按 API ID 取页签描述
   */
  public TableSnapshot getInnerTableDescriptor(String apiId) {
    if (tableDescriptors == null || apiId == null) {
      return null;
    }
    return tableDescriptors.stream().filter(tab -> apiId.equals(tab.getAPIID())).findFirst().orElse(null);
  }

  /**
   * v3 语义：按 API ID 取属性
   */
  public AttributeSnapshot getAttribute(String apiId) {
    if (mainObjectAttributes == null || apiId == null) {
      return null;
    }
    return mainObjectAttributes.get(apiId);
  }

  /**
   * v3 语义：是否配置了全局 Tracker
   */
  public boolean hasGlobalTracker() {
    return globalTrackerId != null;
  }

  public TableSnapshot getInnerTableDescriptor() {
    return (tableDescriptors == null || tableDescriptors.isEmpty()) ? null : tableDescriptors.get(0);
  }

  // ==================== 分类视图（{@link ClassificationFacts}） ====================

  /**
   * 空视图（分类无元数据 / 元数据源缺失时的兜底）。
   */
  public static ClassSnapshot empty(Long type) {
    ClassSnapshot snapshot = new ClassSnapshot();
    snapshot.setType(type);
    snapshot.setId(type);
    snapshot.setEnabled(Boolean.TRUE);
    snapshot.setAttributes(List.of());
    return snapshot;
  }

  @Override
  public List<AttributeFacts> attributes() {
    return attributes == null ? List.of() : List.copyOf(attributes);
  }

  /**
   * 可写属性（具体类型）：内部寻址用——对外契约口径见 {@link #attributes()}。
   */
  private List<AttributeSnapshot> writable() {
    return attributes == null ? List.of() : attributes;
  }

  @Override
  public Optional<AttributeFacts> byColumn(String column) {
    if (column == null) {
      return Optional.empty();
    }
    return writable().stream().filter(attribute -> column.equals(attribute.column()))
      .map(AttributeFacts.class::cast).findFirst();
  }

  @Override
  public Optional<AttributeFacts> byApiId(String apiId) {
    if (apiId == null) {
      return Optional.empty();
    }
    return writable().stream().filter(attribute -> apiId.equals(attribute.apiId())).map(AttributeFacts.class::cast)
      .findFirst();
  }

  @Override
  public Map<String, AttributeFacts> byColumnValues() {
    Map<String, AttributeFacts> byColumn = new LinkedHashMap<>();
    for (AttributeSnapshot attribute : writable()) {
      if (attribute.column() != null && !attribute.column().isBlank()) {
        byColumn.putIfAbsent(attribute.column(), attribute);
      }
    }
    return byColumn;
  }

  @Override
  public Set<String> mainColumns() {
    Set<String> columns = new LinkedHashSet<>();
    for (AttributeSnapshot attribute : writable()) {
      if (attribute.mainApiId() == null) {
        continue;
      }
      byApiId(attribute.mainApiId()).map(AttributeFacts::column).filter(Objects::nonNull).ifPresent(columns::add);
    }
    return columns;
  }

  @Override
  public boolean subItemsTabEnabled() {
    return subItemsTabEnabled;
  }
}
