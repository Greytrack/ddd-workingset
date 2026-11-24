package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
/** 授权所需的对象身份、版次和业务属性。 */
public class BusinessObjectDto implements BusinessDataCarrier {

    private final Long id;
    private final Long baseClassId;
    private final Long classId;
    private final Long revisionId;
    private Long workspaceId;
    private Long trackerId;
    private Long commonId;
    private Long workingsetId;
    private Long parentId;
    private Integer revision;
    private Integer deleteFlag;
    private final Map<Long, List<TabRowDto>> tabs = new LinkedHashMap<>();
    private final Map<String, FieldValue> businessData = new LinkedHashMap<>();

    public BusinessObjectDto(Long id, Long baseClassId, Long classId, Long revisionId) {
        this.id = id;
        this.baseClassId = baseClassId;
        this.classId = classId;
        this.revisionId = revisionId;
    }

    /** 设置已加载页签的完整行集，空列表表示已加载且没有行。 */
    public void setTabRows(Long tabId, List<TabRowDto> rows) {
        tabs.put(Objects.requireNonNull(tabId, "tabId"), List.copyOf(rows));
    }

    /** 未加载的页签返回 null。 */
    public List<TabRowDto> getTabRows(Long tabId) {
        return tabs.get(tabId);
    }

    public Map<Long, List<TabRowDto>> getTabs() {
        return Collections.unmodifiableMap(tabs);
    }

    @Override
    public Map<String, FieldValue> getBusinessData() {
        return businessData;
    }

    @Getter
    @Setter
    /** 页签行的关联身份与属性。revision 是关联目标的版次号。 */
    public static class TabRowDto implements BusinessDataCarrier {
        private final Long id;
        private final Long relatedType;
        private final Long relatedInstanceId;
        private final Integer revision;
        private final Map<String, FieldValue> businessData = new LinkedHashMap<>();

        public TabRowDto(Long id, Long relatedType, Long relatedInstanceId, Integer revision) {
            this.id = id;
            this.relatedType = relatedType;
            this.relatedInstanceId = relatedInstanceId;
            this.revision = revision;
        }

        @Override
        public Map<String, FieldValue> getBusinessData() {
            return businessData;
        }
    }

}
