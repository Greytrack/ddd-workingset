package com.airedgesoft.lib.restful.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LockedVersionSchemaCustomizer} 单元测试（夹具对齐真实 springdoc 形态）：/v1 响应体直接引用或经集合信封（命名 {@code Page*} 组件下钻 / 内联
 * items）引用的组件补 {@code lockedVersion}；Q 前缀读模型与非 Q 前缀但带 {@code name} 属性的子实体载荷（如 {@code PeerReviewItem}） 均补齐；LRO
 * {@code Operation}、无 {@code name} 的 {@code Result*} 错误信封、嵌套值对象、非引用组件与 v3 路径不触碰。
 */
class LockedVersionSchemaCustomizerTest {

    private final LockedVersionSchemaCustomizer customizer = new LockedVersionSchemaCustomizer();

    @Test
    @DisplayName("/v1 单资源、命名 Page 组件下钻条目与非 Q 子实体补 lockedVersion；Operation/Result*/嵌套/未引用组件不触碰")
    void testCustomise_V1ResponseComponents_GainLockedVersion() {
        OpenAPI openApi = new OpenAPI();
        ObjectSchema single = new ObjectSchema();
        ObjectSchema nestedValue = new ObjectSchema();
        ObjectSchema pageWrapper = new ObjectSchema();
        ObjectSchema subEntity = new ObjectSchema();
        ObjectSchema lroOperation = new ObjectSchema();
        ObjectSchema resultVoid = new ObjectSchema();
        ObjectSchema v3LegacyDto = new ObjectSchema();
        openApi.setComponents(new Components().addSchemas("QDemoTask", single).addSchemas("QVotingRules", nestedValue)
                .addSchemas("PageQDemoTask", pageWrapper).addSchemas("PeerReviewItem", subEntity)
                .addSchemas("Operation", lroOperation).addSchemas("ResultVoid", resultVoid)
                .addSchemas("LegacyV3Dto", v3LegacyDto));

        Paths paths = new Paths();
        // 单资源直返：$ref QDemoTask
        paths.addPathItem("/v1/demoTasks/{taskId}",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/QDemoTask"))))))));
        // 集合直返（真实 springdoc 形态）：$ref 命名 Page 组件 → 下钻 items.$ref 命中 QDemoTask
        pageWrapper.properties(Map.of("items", itemsRef("QDemoTask")));
        paths.addPathItem("/v1/demoTasks",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/PageQDemoTask"))))))));
        // 子实体直返：非 Q 前缀但带 string name 属性（springdoc 对嵌套 QPeerReview.Item 产出组件名 PeerReviewItem）
        subEntity.addProperty("name", new StringSchema());
        subEntity.addProperty("status", new Schema().type("string"));
        paths.addPathItem("/v1/peerReviews/{id}/items/{itemId}:approve",
                new PathItem().post(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/PeerReviewItem"))))))));
        // LRO 操作资源：带 name 属性但显式豁免
        lroOperation.addProperty("name", new StringSchema());
        lroOperation.addProperty("done", new Schema().type("boolean"));
        paths.addPathItem("/v1/operations/{operationId}",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/Operation"))))))));
        // 错误信封（无 name、非 Q）：作为错误响应体被收集但不触碰
        resultVoid.addProperty("code", new Schema().type("integer"));
        resultVoid.addProperty("data", new Schema().type("object"));
        resultVoid.addProperty("message", new StringSchema());
        paths.addPathItem("/v1/demoProjects",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("500",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/ResultVoid"))))))));
        paths.addPathItem("/api/legacy/v3/object",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/LegacyV3Dto"))))))));
        openApi.setPaths(paths);
        nestedValue.addProperty("kind", new Schema().type("string"));

        customizer.customise(openApi);

        assertNotNull(single.getProperties(), "单资源组件应获得 lockedVersion");
        assertLockedVersion(single);
        assertNotNull(pageWrapper.getProperties().get("items"), "Page 信封本体不改条目数组");
        assertFalse(pageWrapper.getProperties().containsKey("lockedVersion"), "Page 信封本体不带 lockedVersion");
        assertLockedVersion(subEntity, "非 Q 前缀但带 name 属性的子实体载荷应获得 lockedVersion");
        assertFalse(lroOperation.getProperties().containsKey("lockedVersion"), "LRO Operation 显式豁免");
        assertFalse(resultVoid.getProperties().containsKey("lockedVersion"), "无 name 的 Result* 错误信封不触碰");
        assertFalse(nestedValue.getProperties().containsKey("lockedVersion"), "嵌套值对象（仅被父组件引用）不触碰");
        assertFalse(v3LegacyDto.getProperties() != null && v3LegacyDto.getProperties().containsKey("lockedVersion"),
                "v3 路径引用的组件不触碰");
    }

    @Test
    @DisplayName("内联集合信封（防未来形态）与 Q 前缀无 name 组件（统计投影类）同样补齐")
    void testCustomise_InlinePageEnvelope_AndQProjection_AreCovered() {
        OpenAPI openApi = new OpenAPI();
        ObjectSchema listItem = new ObjectSchema();
        ObjectSchema stats = new ObjectSchema();
        openApi.setComponents(
                new Components().addSchemas("QDemoTask", listItem).addSchemas("QPeerReviewApprovalStats", stats));

        Paths paths = new Paths();
        // 内联信封（手搓模型形态）
        paths.addPathItem("/v1/demoTasks",
                new PathItem().get(
                        new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(
                                new Content().addMediaType("*/*", new MediaType().schema(inlinePage("QDemoTask"))))))));
        // Q 前缀统计投影：无 name 属性（rows/summary 形态），经 Q 闸门补齐
        stats.addProperty("rows", new ArraySchema());
        stats.addProperty("summary", new Schema().type("object"));
        paths.addPathItem("/v1/peerReviews/{id}/approvalStats",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*", new MediaType()
                                .schema(new Schema().$ref("#/components/schemas/QPeerReviewApprovalStats"))))))));
        openApi.setPaths(paths);

        customizer.customise(openApi);

        assertLockedVersion(listItem, "内联信封条目目标应获得 lockedVersion");
        assertLockedVersion(stats, "Q 前缀读模型（无 name 属性的投影）应获得 lockedVersion");
    }

    @Test
    @DisplayName("幂等：已含 lockedVersion 的组件不重复追加；空 paths/components 安全跳过")
    void testCustomise_IdempotentAndNullSafe() {
        OpenAPI openApi = new OpenAPI();
        ObjectSchema already = new ObjectSchema();
        already.addProperty("lockedVersion", new Schema().type("integer"));
        openApi.setComponents(new Components().addSchemas("QDemoTask", already));
        Paths paths = new Paths();
        paths.addPathItem("/v1/demoTasks/{taskId}",
                new PathItem().get(new Operation().responses(new ApiResponses().addApiResponse("200",
                        new ApiResponse().content(new Content().addMediaType("*/*",
                                new MediaType().schema(new Schema().$ref("#/components/schemas/QDemoTask"))))))));
        openApi.setPaths(paths);

        customizer.customise(openApi);
        assertEquals(1, already.getProperties().size(), "已有 lockedVersion 不重复追加");

        OpenAPI empty = new OpenAPI();
        customizer.customise(empty);
        assertNull(empty.getComponents(), "无 paths 时安全跳过");
    }

    private static ArraySchema itemsRef(String component) {
        ArraySchema items = new ArraySchema();
        items.setItems(new Schema().$ref("#/components/schemas/" + component));
        return items;
    }

    private static ObjectSchema inlinePage(String itemComponent) {
        ObjectSchema page = new ObjectSchema();
        page.addProperty("items", itemsRef(itemComponent));
        page.addProperty("nextPageToken", new StringSchema());
        return page;
    }

    private static void assertLockedVersion(Schema<?> schema) {
        assertLockedVersion(schema, null);
    }

    private static void assertLockedVersion(Schema<?> schema, String message) {
        Schema<?> lockedVersion = schema.getProperties().get("lockedVersion");
        assertNotNull(lockedVersion, message);
        assertEquals("int64", lockedVersion.getFormat(), message);
        assertTrue(Boolean.TRUE.equals(lockedVersion.getReadOnly()), message);
    }
}
