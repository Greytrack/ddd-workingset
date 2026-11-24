package com.airedgesoft.lib.restful.abac;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code _access} 伴随元数据节点（§19.2 wire 契约；{@code ?includeAccess=true} 时随资源返回，缺省不出现）。
 *
 * <p>
 * wire 形态（§19.6 示例）：
 *
 * <pre>
 * "_access": { "allowedActions": ["demoTaskGet"], "writableFields": [],
 *              "maskedFields": ["estimatedCostCents"],
 *              "valueConstraints": { "owner.department": ["ENGINEERING"] } }
 * </pre>
 *
 * <p>
 * <b>挂载方式</b>：BC 的 {@code Q*} record 声明可空组件 {@code @JsonProperty("_access") AccessNode access}（§19.5 登记的法定形态）， 或实现
 * {@link AccessAttachable} 交由 {@link IncludeAccessAspect} 自动挂载（横切，不改 Controller 代码）。
 */
public record AccessNode(List<String> allowedActions, List<String> writableFields, List<String> maskedFields,
        Map<String, List<String>> valueConstraints) {

    public AccessNode {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        writableFields = writableFields == null ? List.of() : List.copyOf(writableFields);
        maskedFields = maskedFields == null ? List.of() : List.copyOf(maskedFields);
        valueConstraints = valueConstraints == null ? Map.of() : Map.copyOf(valueConstraints);
    }

    /** 由裁决结果构造 wire 节点（map 值集转 List 保序）。 */
    public static AccessNode from(AccessMetadata metadata) {
        Map<String, List<String>> constraints = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : metadata.valueConstraints().entrySet()) {
            constraints.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new AccessNode(List.copyOf(metadata.allowedActions()), List.copyOf(metadata.writableFields()),
                List.copyOf(metadata.maskedFields()), constraints);
    }
}
