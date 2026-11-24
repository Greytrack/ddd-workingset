package com.airedgesoft.lib.restful.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

/**
 * 读模型响应 schema 的 {@code lockedVersion} 补齐（决策 C5：{@code lockedVersion} 必须显式出现在 schema 中）。
 *
 * <p>
 * <b>背景</b>：Controller 声明裸 {@code Q*} 返回类型（{@code EtagResponseAdvice} 在运行时动态包装注入 {@code lockedVersion}），springdoc 生成的
 * {@code Q*} 组件因此天然不含该字段；{@link EtagSchemaCustomizer} 只展开显式 {@code Etagged*} 包装组件，覆盖不到裸返回形态。 本定制器把 <b>/v1
 * 路径响应体直接引用（单资源）或经集合信封 {@code items} 引用（列表条目）</b> 的组件补上 {@code lockedVersion} （int64、只读），与运行时 wire
 * 形态一致——单资源与列表条目都会被平铺注入。
 *
 * <p>
 * <b>集合信封恒为命名组件</b>：springdoc 对 {@code Page<T>} 生成命名组件并以 {@code $ref: PageQXxx} 引用（内联 {@code properties.items}
 * 形态仅存在于手搓模型）——因此响应体为 {@code $ref} 指向<b>非资源组件</b>时，须取该组件的 {@code items.$ref} 下钻一层收集条目目标。
 *
 * <p>
 * 收窄口径：只补「运行时会注入」的资源载荷——组件须为 <b>{@code Q} 前缀读模型</b>（含统计/图投影等无 {@code name} 属性的 Q*） <b>或带 {@code name}
 * 属性的资源载荷</b>（子实体直返，如嵌套 {@code QPeerReview.Item} 在 spec 中组件名为 {@code PeerReviewItem}）。 不带 {@code name} 的信封 / 回执 /
 * 错误形态（{@code Result*}、批量回执、{@code ProblemDetail}）与 LRO {@code Operation} 资源 （lib
 * 自有，操作不参与并发代数）不触碰；嵌套值对象（{@code QRevision} / {@code AccessNode} 等，仅被父组件引用）不进响应体收集集，天然免疫。
 */
public class LockedVersionSchemaCustomizer implements GlobalOpenApiCustomizer {

    private static final String URI_PREFIX = "/v1/";

    private static final String LOCKED_VERSION = "lockedVersion";

    private static final String READ_MODEL_PREFIX = "Q";

    private static final String ITEMS_PROPERTY = "items";

    private static final String NAME_PROPERTY = "name";

    /** LRO 操作资源（{@code OperationsController} 出参；操作无并发代数）——带 {@code name} 属性但显式豁免。 */
    private static final String LRO_OPERATION_COMPONENT = "Operation";

    @Override
    public void customise(OpenAPI openApi) {
        Set<String> bodyComponents = collectResponseBodyComponents(openApi);
        Components components = openApi.getComponents();
        if (bodyComponents.isEmpty() || components == null || components.getSchemas() == null) {
            return;
        }
        components.getSchemas().forEach((name, schema) -> {
            if (bodyComponents.contains(name) && eligible(name, schema) && schema != null) {
                addLockedVersion(schema);
            }
        });
    }

    /** 收集 /v1 路径下作为响应体出现（直接 $ref 或集合信封 items 条目，含命名包装组件下钻）的组件名。 */
    private Set<String> collectResponseBodyComponents(OpenAPI openApi) {
        Set<String> names = new LinkedHashSet<>();
        if (openApi.getPaths() == null) {
            return names;
        }
        Components components = openApi.getComponents();
        openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.startsWith(URI_PREFIX) || pathItem == null) {
                return;
            }
            for (Operation operation : pathItem.readOperations()) {
                if (operation == null || operation.getResponses() == null) {
                    continue;
                }
                operation.getResponses().values().forEach(response -> collect(response, names, components));
            }
        });
        return names;
    }

    private void collect(ApiResponse response, Set<String> names, Components components) {
        if (response == null || response.getContent() == null) {
            return;
        }
        response.getContent().values().forEach(
                mediaType -> collectSchema(mediaType == null ? null : mediaType.getSchema(), names, components));
    }

    private void collectSchema(Schema<?> schema, Set<String> names, Components components) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            names.add(componentName(schema.get$ref()));
            // 命名集合信封（PageQXxx）：下钻一层取 items 条目目标——列表条目与单资源同样被运行时注入 lockedVersion
            if (components != null && components.getSchemas() != null) {
                collectPageItems(components.getSchemas().get(componentName(schema.get$ref())), names);
            }
            return;
        }
        // 内联集合信封：ObjectSchema.properties.items（数组）的条目 $ref
        collectPageItems(schema, names);
    }

    private void collectPageItems(Schema<?> schema, Set<String> names) {
        if (schema != null && schema.getProperties() != null
                && schema.getProperties().get(ITEMS_PROPERTY) instanceof ArraySchema items && items.getItems() != null
                && items.getItems().get$ref() != null) {
            names.add(componentName(items.getItems().get$ref()));
        }
    }

    /** 注入资格：Q 前缀读模型，或带 string {@code name} 属性的资源载荷；LRO 操作资源显式豁免。 */
    private static boolean eligible(String componentName, Schema<?> schema) {
        if (LRO_OPERATION_COMPONENT.equals(componentName)) {
            return false;
        }
        return componentName.startsWith(READ_MODEL_PREFIX) || hasNameProperty(schema);
    }

    private static boolean hasNameProperty(Schema<?> schema) {
        return schema != null && schema.getProperties() != null
                && schema.getProperties().get(NAME_PROPERTY) instanceof Schema<?> nameSchema
                && "string".equals(nameSchema.getType());
    }

    private static String componentName(String ref) {
        int slash = ref.lastIndexOf('/');
        return slash < 0 ? ref : ref.substring(slash + 1);
    }

    private void addLockedVersion(Schema<?> schema) {
        if (schema.getProperties() != null && schema.getProperties().containsKey(LOCKED_VERSION)) {
            return;
        }
        IntegerSchema lockedVersion = new IntegerSchema();
        lockedVersion.format("int64");
        lockedVersion.readOnly(Boolean.TRUE);
        lockedVersion.description("并发版本（存储版本 + 1 的 v-N 代数；写请求回传做冲突检测，不一致 409）");
        schema.addProperty(LOCKED_VERSION, lockedVersion);
    }
}
