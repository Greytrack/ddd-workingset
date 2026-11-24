package com.airedgesoft.lib.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.PackageVersion;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.google.common.collect.Lists;
import jodd.util.StringPool;
import jodd.util.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Jackson工具类
 *
 * @author cjc@airedgesoft.com
 */
@Slf4j
public class JsonUtil {

  protected JsonUtil() {
  }

  public static boolean isJson(String str) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.readTree(str);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 将对象序列化成json字符串
   *
   * @param value javaBean
   * @param <T>   T 泛型标记
   * @return jsonString json字符串
   */
  public static <T> String toJson(T value) {
    try {
      return getInstance().writeValueAsString(value);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * 将对象序列化成 json byte 数组
   *
   * @param object javaBean
   * @return jsonString json字符串
   */
  public static byte[] toJsonAsBytes(Object object) {
    try {
      return getInstance().writeValueAsBytes(object);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param content   content
   * @param valueType class
   * @param <T>       T 泛型标记
   * @return Bean
   */
  public static <T> T parse(String content, Class<T> valueType) {
    try {
      return getInstance().readValue(content, valueType);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * 将json反序列化成对象
   *
   * @param content       content
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  public static <T> T parse(String content, TypeReference<T> typeReference) {
    try {
      return getInstance().readValue(content, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json byte 数组反序列化成对象
   *
   * @param bytes     json bytes
   * @param valueType class
   * @param <T>       T 泛型标记
   * @return Bean
   */
  public static <T> T parse(byte[] bytes, Class<T> valueType) {
    try {
      return getInstance().readValue(bytes, valueType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param bytes         bytes
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  public static <T> T parse(byte[] bytes, TypeReference<T> typeReference) {
    try {
      return getInstance().readValue(bytes, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param in        InputStream
   * @param valueType class
   * @param <T>       T 泛型标记
   * @return Bean
   */
  public static <T> T parse(InputStream in, Class<T> valueType) {
    try {
      return getInstance().readValue(in, valueType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param in            InputStream
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  public static <T> T parse(InputStream in, TypeReference<T> typeReference) {
    try {
      return getInstance().readValue(in, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成List对象
   *
   * @param content      content
   * @param valueTypeRef class
   * @param <T>          T 泛型标记
   * @return List
   */
  public static <T> List<T> parseArray(String content, Class<T> valueTypeRef) {
    try {

      if (!StringUtil.startsWithIgnoreCase(content, StringPool.LEFT_SQ_BRACKET)) {
        content = StringPool.LEFT_SQ_BRACKET + content + StringPool.RIGHT_SQ_BRACKET;
      }

      List<Map<String, Object>> list = getInstance().readValue(content, new TypeReference<>() {
      });
      List<T> result = new ArrayList<>();
      for (Map<String, Object> map : list) {
        result.add(toPojo(map, valueTypeRef));
      }
      return result;
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
    return Lists.newArrayList();
  }

  /**
   * 将json反序列化成List对象
   *
   * @param content content
   * @param clazz   class
   * @param <T>     T 泛型标记
   * @return List
   */
  public static <T> List<T> parseArray2(String content, Class<T> clazz) {
    try {
      if (!StringUtils.hasText(content)) {
        return new ArrayList<>();
      }
      ObjectMapper mapper = getInstance();
      return mapper.readValue(content, getInstance().getTypeFactory().constructCollectionType(List.class, clazz));
    } catch (JsonProcessingException e) {
      log.error("Error parsing JSON array: {}", e.getMessage(), e);
      return new ArrayList<>();
    }
  }

  /**
   * 将json反序列化成List对象【自动添加[]】
   *
   * @param content json字符串
   * @param clazz   类型
   * @param <T>     泛型
   * @return
   */
  public static <T> List<T> parseArrayAutoAddSqBracket(String content, Class<T> clazz) {
    try {
      if (!StringUtils.hasText(content)) {
        return new ArrayList<>();
      }

      if (!content.startsWith(StringPool.LEFT_SQ_BRACKET)) {
        content = StringPool.LEFT_SQ_BRACKET + content + StringPool.RIGHT_SQ_BRACKET;
      }

      ObjectMapper mapper = getInstance();
      return mapper.readValue(content, getInstance().getTypeFactory().constructCollectionType(List.class, clazz));
    } catch (JsonProcessingException e) {
      log.error("Error parsing JSON array: {}", e.getMessage(), e);
      return new ArrayList<>();
    }
  }

  public static Map<String, Object> toMap(String content) {
    try {
      return getInstance().readValue(content, Map.class);
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
    return new HashMap<>(16);
  }

  public static <T> Map<String, T> toMap(String content, Class<T> valueTypeRef) {
    try {
      Map<String, Map<String, Object>> map = getInstance().readValue(content,
        new TypeReference<Map<String, Map<String, Object>>>() {
        });
      Map<String, T> result = new HashMap<>(16);
      for (Map.Entry<String, Map<String, Object>> entry : map.entrySet()) {
        result.put(entry.getKey(), toPojo(entry.getValue(), valueTypeRef));
      }
      return result;
    } catch (IOException e) {
      log.error(e.getMessage(), e);
    }
    return new HashMap<>();
  }

  public static <T> T toPojo(Map<String, Object> fromValue, Class<T> toValueType) {
    return getInstance().convertValue(fromValue, toValueType);
  }

  /**
   * 将json字符串转成 JsonNode
   *
   * @param jsonString jsonString
   * @return jsonString json字符串
   */
  public static JsonNode readTree(String jsonString) {
    try {
      return getInstance().readTree(jsonString);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json字符串转成 JsonNode
   *
   * @param in InputStream
   * @return jsonString json字符串
   */
  public static JsonNode readTree(InputStream in) {
    try {
      return getInstance().readTree(in);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json字符串转成 JsonNode
   *
   * @param content content
   * @return jsonString json字符串
   */
  public static JsonNode readTree(byte[] content) {
    try {
      return getInstance().readTree(content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json byte 数组反序列化成对象
   *
   * @param content   json bytes
   * @param valueType class
   * @param <T>       T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable byte[] content, Class<T> valueType) {
    if (ObjectUtils.isEmpty(content)) {
      return null;
    }
    try {
      return getInstance().readValue(content, valueType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param jsonString jsonString
   * @param valueType  class
   * @param <T>        T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable String jsonString, Class<T> valueType) {
    if (StringUtil.isBlank(jsonString)) {
      return null;
    }
    try {
      return getInstance().readValue(jsonString, valueType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public static <T> T readValue(@Nullable Object json, Class<T> valueType) {
    if (Objects.isNull(json)) {
      return null;
    }
    try {
      return getInstance().convertValue(json, valueType);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param in        InputStream
   * @param valueType class
   * @param <T>       T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable InputStream in, Class<T> valueType) {
    if (in == null) {
      return null;
    }
    try {
      return getInstance().readValue(in, valueType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param content       bytes
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable byte[] content, TypeReference<T> typeReference) {
    if (ObjectUtils.isEmpty(content)) {
      return null;
    }
    try {
      return getInstance().readValue(content, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param jsonString    jsonString
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable String jsonString, TypeReference<T> typeReference) {
    if (StringUtil.isBlank(jsonString)) {
      return null;
    }
    try {
      return getInstance().readValue(jsonString, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json反序列化成对象
   *
   * @param in            InputStream
   * @param typeReference 泛型类型
   * @param <T>           T 泛型标记
   * @return Bean
   */
  @Nullable
  public static <T> T readValue(@Nullable InputStream in, TypeReference<T> typeReference) {
    if (in == null) {
      return null;
    }
    try {
      return getInstance().readValue(in, typeReference);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将json字符串转成 JsonNode
   *
   * @param jsonParser JsonParser
   * @return jsonString json字符串
   */
  public static JsonNode readTree(JsonParser jsonParser) {
    try {
      return getInstance().readTree(jsonParser);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 转换JsonNode为List
   */
  public static List<JsonNode> convertJsonNodeToList(JsonNode jsonNode) {
    if (jsonNode.isArray()) {
      return StreamSupport.stream(jsonNode.spliterator(), false).toList();
    } else {
      return Lists.newArrayList(jsonNode);
    }
  }

  public static ObjectMapper getInstance() {
    return JacksonHolder.INSTANCE.getObjectMapper();
  }

  @Getter
  public enum JacksonHolder {

    INSTANCE;

    private final ObjectMapper objectMapper;

    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";

    public static class AirEdgeJavaTimeModule extends SimpleModule {

      public AirEdgeJavaTimeModule() {
        super(PackageVersion.VERSION);
        this.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(PATTERN_DATETIME)));
        this.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(PATTERN_DATE)));
        this.addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(PATTERN_TIME)));
        this.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(PATTERN_DATETIME)));
        this.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(PATTERN_DATE)));
        this.addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(PATTERN_TIME)));
      }

    }

    JacksonHolder() {
      objectMapper = new ObjectMapper();
      objectMapper.setLocale(Locale.CHINA);
      // 去掉默认的时间戳格式
      objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
      // 设置为中国上海时区
      objectMapper.setTimeZone(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")));
      // 序列化时，日期的统一格式
      objectMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA));
      // 序列化处理
      objectMapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
      objectMapper.configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true);
      objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
      objectMapper.findAndRegisterModules();
      // 失败处理
      objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      // 单引号处理
      objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
      // 反序列化时，属性不存在的兼容处理s
      objectMapper.getDeserializationConfig().withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      // 日期格式化
      objectMapper.registerModule(new AirEdgeJavaTimeModule());
      objectMapper.findAndRegisterModules();
    }
  }

  /**
   * 判断jsonNode是否为空
   */
  public static boolean jsonNodeIsEmpty(JsonNode jsonNode) {
    return jsonNode == null || jsonNode.isEmpty() || "null".equals(jsonNode.asText());
  }
}
