package com.airedgesoft.sharedkernel.businessframework.infrastructure.repositories.businessobject;

import cn.hutool.core.util.HashUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v3 共表富文本内容读写（{@code air_inst_rich_text}）：版次行的富文本列存<b>内容行 id</b>，内容在本表。
 *
 * <p>
 * <b>字段口径</b>（与 v3 落库行逐列比对）：{@code db_table_id} ＝ 版次行 id（首版次即实例 id）、{@code db_table_name} ＝ 版次表名、
 * {@code attribute_api_id} ＝ 属性协议 apiId、{@code hash} ＝ {@code HashUtil.tianlHash(content)}、{@code size} ＝ UTF-8 字节 + 20% + 128、
 * {@code content_type} ＝ {@code JSONB}、{@code delete_flag} ＝ 0。
 *
 * <p>
 * 本类只管表协议，不解释 apiId 与列名的对应关系（那属各 BC 的属性模型）。
 */
final class AirInstRichTextStore {

    private static final String SELECT_BY_REVISION_ROWS = "SELECT db_table_id, attribute_api_id, content FROM "
            + RevisionRepositorySupport.Protocol.RICH_TEXT_TABLE + " WHERE delete_flag = 0 AND db_table_id IN (%s)";

    private static final String SELECT_ID = "SELECT id FROM " + RevisionRepositorySupport.Protocol.RICH_TEXT_TABLE
            + " WHERE db_table_id = ? AND attribute_api_id = ? AND delete_flag = 0";

    /** 写内容行：同一 id 命中则改写内容（列里存的 id 不变，v3 按 id 读到的始终是最新内容）。 */
    private static final String UPSERT = "INSERT INTO " + RevisionRepositorySupport.Protocol.RICH_TEXT_TABLE
            + " (id, db_table_id, db_table_name, attribute_api_id, hash, content, size, content_type, accessed_time,"
            + " lock_version, created_by, created_time, updated_by, updated_time, delete_flag, tracker)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, 'JSONB', ?, 0, ?, ?, ?, ?, 0, NULL)"
            + " ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, hash = EXCLUDED.hash,"
            + " size = EXCLUDED.size, updated_by = EXCLUDED.updated_by, updated_time = EXCLUDED.updated_time,"
            + " accessed_time = EXCLUDED.accessed_time";

    /** 取消签出：丢弃版次的内容行随之清退（版次行已删除，残留内容行会成孤儿）。 */
    private static final String DELETE_BY_REVISION_ROW = "DELETE FROM "
            + RevisionRepositorySupport.Protocol.RICH_TEXT_TABLE + " WHERE db_table_id = ?";

    /** v3 富文本固定元数据开销（{@code InstRichTextBO#calculateRichTextSize}）。 */
    private static final long METADATA_OVERHEAD = 128L;

    private final JdbcTemplate jdbcTemplate;

    AirInstRichTextStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批量读取若干版次行的内容（版次行 id → apiId → 内容）。
     *
     * @param revisionRowIds
     *            版次行 id 清单（空集返回空）
     * @return 内容行；无内容的版次行不出现在结果里
     */
    Map<Long, Map<String, String>> findByRevisionRowIds(List<Long> revisionRowIds) {
        if (revisionRowIds == null || revisionRowIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(revisionRowIds.size(), "?"));
        Map<Long, Map<String, String>> contents = new LinkedHashMap<>();
        jdbcTemplate.query(String.format(SELECT_BY_REVISION_ROWS, placeholders), rs -> {
            contents.computeIfAbsent(rs.getLong("db_table_id"), key -> new LinkedHashMap<>())
                    .put(rs.getString("attribute_api_id"), rs.getString("content"));
        }, revisionRowIds.toArray());
        return contents;
    }

    /** 某版次行某属性的既有内容行 id（无则空）：更新路径复用 id 的关键。 */
    Long existingId(long revisionRowId, String apiId) {
        List<Long> ids = jdbcTemplate.queryForList(SELECT_ID, Long.class, revisionRowId, apiId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 写入/改写内容行（id 已在写计划期定好，与版次列里的值同源）。
     *
     * @param tableName
     *            版次表名（{@code db_table_name}，各 BC 自己的版次表）
     * @param revisionRowId
     *            版次行 id
     * @param apiIdToContent
     *            apiId → 内容
     * @param apiIdToId
     *            apiId → 内容行 id
     * @param actor
     *            操作人列值
     * @param now
     *            审计时间戳
     */
    void upsert(String tableName, long revisionRowId, Map<String, String> apiIdToContent, Map<String, Long> apiIdToId, String actor,
            long now) {

        apiIdToContent.forEach((apiId, content) -> {
            Long id = apiIdToId.get(apiId);
            if (id == null || content == null) {
                return;
            }
            jdbcTemplate.update(UPSERT, id, revisionRowId, tableName, apiId, String.valueOf(HashUtil.tianlHash(content)),
                    content, calculateSize(content), now, actor, now, actor, now);
        });
    }

    /** 删除某版次行的全部内容行（取消签出丢弃版次）。 */
    void delete(long revisionRowId) {
        jdbcTemplate.update(DELETE_BY_REVISION_ROW, revisionRowId);
    }

    /** v3 口径：内容 UTF-8 字节 + 20% JSON 结构开销 + 128 元数据开销。 */
    static long calculateSize(String content) {
        if (content == null) {
            return 0L;
        }
        long base = content.getBytes(StandardCharsets.UTF_8).length;
        return base + base * 20 / 100 + METADATA_OVERHEAD;
    }
}
