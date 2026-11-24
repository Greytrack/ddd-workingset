package com.airedgesoft.lib.restful.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

/**
 * {@code Etagged<T>} 的 OpenAPI 展开器：wire 上资源字段是<b>平铺</b>的（{@code @JsonUnwrapped}），但 springdoc 默认会生成 {@code { resource:
 * {...}, lockedVersion: N }} 的嵌套形态。
 *
 * <p>
 * 本定制器把所有名称以 {@code Etagged} 开头的组件 schema 改写为「被包装资源字段 + {@code lockedVersion}」的平铺形态， 使生成客户端看到的单资源响应 / 列表条目字段与真实报文一致（决策
 * C5：{@code lockedVersion} 必须显式出现在 schema 中）。
 */
public class EtagSchemaCustomizer implements GlobalOpenApiCustomizer {

    private static final String WRAPPER_PREFIX = "Etagged";
    private static final String RESOURCE_PROPERTY = "resource";
    private static final String LOCKED_VERSION = "lockedVersion";

    @Override
    public void customise(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null || components.getSchemas() == null) {
            return;
        }
        components.getSchemas().replaceAll(
                (name, schema) -> name.startsWith(WRAPPER_PREFIX) ? flatten(name, schema, components) : schema);
    }

    private Schema<?> flatten(String name, Schema<?> wrapper, Components components) {
        if (!(wrapper instanceof ObjectSchema) || wrapper.getProperties() == null
                || !wrapper.getProperties().containsKey(RESOURCE_PROPERTY)) {
            return wrapper;
        }
        Schema<?> resource = resolve(wrapper.getProperties().get(RESOURCE_PROPERTY), components);
        if (resource == null) {
            return wrapper;
        }
        Map<String, Schema> properties = new LinkedHashMap<>();
        if (resource.getProperties() != null) {
            resource.getProperties().forEach((key, value) -> properties.put(key, (Schema) value));
        }
        Schema<?> lockedVersion = (Schema<?>) wrapper.getProperties().get(LOCKED_VERSION);
        if (lockedVersion != null) {
            properties.put(LOCKED_VERSION, lockedVersion);
        }
        Set<String> required = new LinkedHashSet<>();
        if (resource.getRequired() != null) {
            required.addAll(resource.getRequired());
        }
        ObjectSchema flattened = new ObjectSchema();
        flattened.setDescription(wrapper.getDescription());
        flattened.setProperties(properties);
        if (!required.isEmpty()) {
            flattened.setRequired(new java.util.ArrayList<>(required));
        }
        return flattened;
    }

    private Schema<?> resolve(Schema<?> schema, Components components) {
        if (schema == null) {
            return null;
        }
        String ref = schema.get$ref();
        if (ref == null) {
            return schema;
        }
        int slash = ref.lastIndexOf('/');
        String name = slash < 0 ? ref : ref.substring(slash + 1);
        return components.getSchemas() == null ? null : components.getSchemas().get(name);
    }
}
