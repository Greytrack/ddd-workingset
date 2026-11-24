package com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.model;

import lombok.Data;

import java.util.List;

/** 元数据节点快照（替代 v3 {@code INode}/{@code BaseNode}）：判权只读取标识与子节点。 */
@Data
public class NodeSnapshot {

    private Long id;

    private String apiId;

    private String name;

    private List<NodeSnapshot> childNodes;

    public String getAPIID() {
        return apiId;
    }

    public String getApiId() {
        return apiId;
    }
}
