package com.airedgesoft.lib.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
public class RichTextContentUtil extends JsonUtil {

    public RichTextContentUtil() {
        super();
    }

    /**
     * 判断JSON字符串的type是否为doc模式
     *
     * @param json
     *            JSON字符串
     * @return true表示type为doc模式，false表示不是
     */
    public static boolean isDocType(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return false;
        }
        try {
            JsonNode jsonNode = JsonUtil.readTree(json);
            String type = getNodeText(jsonNode, "type");
            return "doc".equals(type);
        } catch (Exception e) {
            log.error("判断JSON type异常：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从JSON字符串解析文档
     *
     * @param json
     *            JSON格式数据
     * @return JsonNode文档对象
     */
    public static JsonNode parseDocument(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return createEmptyDocument();
        }
        try {
            return JsonUtil.readTree(json);
        } catch (Exception e) {
            log.error("富文本Json 解析异常：{}", e.getMessage(), e);
            return toDocumentNode(json);
        }
    }

    /**
     * 创建空文档
     *
     * @return 空文档JsonNode
     */
    private static JsonNode createEmptyDocument() {
        ObjectMapper objectMapper = JsonUtil.getInstance();
        ObjectNode documentNode = objectMapper.createObjectNode();
        documentNode.put("type", "doc");
        return documentNode;
    }

    /**
     * 将纯文本转换为文档节点
     *
     * @param text
     *            纯文本内容
     * @return JsonNode文档节点
     */
    public static JsonNode toDocumentNode(String text) {
        ObjectMapper objectMapper = JsonUtil.getInstance();
        ObjectNode documentNode = objectMapper.createObjectNode();
        documentNode.put("type", "doc");

        if (Objects.isNull(text)) {
            return documentNode;
        }

        ArrayNode contentArray = documentNode.putArray("content");
        ObjectNode paragraphNode = contentArray.addObject();
        paragraphNode.put("type", "paragraph");

        ArrayNode paragraphContent = paragraphNode.putArray("content");
        ObjectNode textNode = paragraphContent.addObject();
        textNode.put("type", "text");
        textNode.put("text", text);

        return documentNode;
    }

    /**
     * 判断JSON中纯文本 是否为空，适用于需求中组件，其他组件不适用
     *
     * @param jsonStr
     *            字符串
     * @return 只校验 type 中为doc ；其他直接返回true
     */
    public static boolean extractPlainTextIsEmpty(String jsonStr) {
        if (ObjectUtils.isEmpty(jsonStr)) {
            return true;
        }
        boolean flag = isDocType(jsonStr);
        // 非需求组件的json格式默认返回false
        if (!flag) {
            return false;
        }
        JsonNode rootNode = RichTextContentUtil.parseDocument(jsonStr);
        boolean text = ObjectUtils.isEmpty(extractPlainText(rootNode));
        if (!text) {
            return false;
        }

        return extractUrlsIsEmpty(rootNode);
    }

    /**
     * 提取纯文本内容
     *
     * @param rootNode
     *            根节点
     * @return 纯文本字符串
     */
    public static String extractPlainText(JsonNode rootNode) {
        if (rootNode.isNumber()) {
            return rootNode.asText("");
        }
        StringBuilder textBuilder = new StringBuilder();
        traverseNodes(rootNode, node -> {
            String type = getNodeText(node, "type");
            if ("text".equals(type) && node.has("text")) {
                textBuilder.append(getNodeText(node, "text"));
            }
        });
        return textBuilder.toString();
    }

    /**
     * 提取纯文本内容
     *
     * @param json
     *            字符串
     * @return 纯文本字符串
     */
    public static String extractPlainTextByJsonString(String json) {
        if (ObjectUtils.isEmpty(json)) {
            return json;
        }
        try {
            JsonNode jsonNode = JsonUtil.readTree(json);
            return extractPlainText(jsonNode);
        } catch (Exception e) {
            log.error("提取纯文本内容异常：", e);
            // 兼容历史数据提取
            return "";
        }
    }

    // ==================== 节点遍历工具方法 ====================

    /**
     * 递归遍历所有节点
     *
     * @param node
     *            当前节点
     * @param processor
     *            节点处理器
     */
    public static void traverseNodes(JsonNode node, Consumer<JsonNode> processor) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        // 处理当前节点
        processor.accept(node);

        // 递归处理content数组
        if (node.has("content")) {
            JsonNode content = node.get("content");
            if (content.isArray()) {
                for (JsonNode child : content) {
                    traverseNodes(child, processor);
                }
            }
        }
    }

    /**
     * 安全获取节点文本值
     *
     * @param node
     *            JSON节点
     * @param key
     *            键名
     * @return 文本值,不存在返回null
     */
    private static String getNodeText(JsonNode node, String key) {
        if (node == null || !node.has(key)) {
            return null;
        }
        JsonNode valueNode = node.get(key);
        return valueNode.isTextual() ? valueNode.asText() : null;
    }

    /**
     * 文档对象转换为JSON字符串
     *
     * @param document
     *            JsonNode文档对象
     * @return JSON格式字符串
     * @throws JsonProcessingException
     *             JSON处理异常
     */
    public static String toJsonString(JsonNode document) throws JsonProcessingException {
        if (Objects.isNull(document) || document.isMissingNode()) {
            return null;
        }
        return toJson(document);
    }

    /**
     * 从JSON节点中提取所有资源URL
     *
     * @param rootNode
     *            根节点
     * @return 资源记录列表
     */
    public static boolean extractUrlsIsEmpty(JsonNode rootNode) {
        List<String> urls = new ArrayList<>();
        traverseNodes(rootNode, node -> {
            String type = getNodeText(node, "type");
            if ("image".equals(type) || "imageHotpots".equals(type) || "attachment".equals(type)) {
                extractUrlFromResourceNode(node, urls);
            }
        });
        return ObjectUtils.isEmpty(urls);
    }

    /**
     * 从资源节点提取URL
     *
     * @param node
     *            资源节点
     * @param urls
     *            URL集合
     */
    private static void extractUrlFromResourceNode(JsonNode node, List<String> urls) {
        JsonNode attrs = node.get("attrs");
        if (attrs == null || attrs.isMissingNode()) {
            return;
        }
        String objectKey = getNodeText(attrs, "objectKey");

        if (objectKey != null && !objectKey.isEmpty()) {
            urls.add(objectKey);
        }
    }
}
