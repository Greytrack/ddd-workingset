package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Page Token（AIP-158 游标）编解码单测：往返解析、filter 指纹绑定、AES-GCM 防篡改 / 防解码。 */
class PageTokensTest {

    private final PageTokens tokens = new PageTokens(PageTokens.DEFAULT_DEV_SECRET, Duration.ofMinutes(30L));

    @Test
    void roundTrip_preservesCursor() {
        String token = tokens.nextToken(623809242866016L, null);
        assertThat(token).isNotBlank();
        assertThat(tokens.parse(token, null)).isEqualTo(623809242866016L);
    }

    @Test
    void token_doesNotLeakPayloadPlaintext() {
        // 加密游标：载荷对客户端不可见（对齐原书 ch21，不再 HMAC 裸载荷）
        String token = tokens.nextToken(623809242866016L, null);
        assertThat(token).doesNotContain("623809242866016");
    }

    @Test
    void parse_rejectsTamperedToken() {
        String token = tokens.nextToken(1L, null);
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThatThrownBy(() -> tokens.parse(tampered, null)).isInstanceOf(DomainException.class)
                .hasMessageContaining("pageToken");
    }

    @Test
    void parse_rejectsTokenFromOtherKey() {
        // 不同密钥派生：同 token 无法跨密钥解密（密钥轮换语义）
        PageTokens other = new PageTokens("another-secret", Duration.ofMinutes(30L));
        String token = tokens.nextToken(1L, null);
        assertThatThrownBy(() -> other.parse(token, null)).isInstanceOf(DomainException.class)
                .hasMessageContaining("pageToken");
    }

    @Test
    void parse_rejectsFilterMismatch() {
        String token = tokens.nextToken(1L, "status=\"ACTIVE\"");
        assertThatThrownBy(() -> tokens.parse(token, "status=\"DONE\"")).isInstanceOf(DomainException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void parse_nullOrBlank_returnsNull() {
        assertThat(tokens.parse(null, null)).isNull();
        assertThat(tokens.parse("", null)).isNull();
    }

    @Test
    void nextToken_nullId_returnsNull() {
        assertThat(tokens.nextToken(null, null)).isNull();
    }

    // ════════ 复合排序键游标（可选 sortKeys，orderBy + 主键复合 seek） ════════

    @Test
    void roundTripWithSortKeys_preservesOrderAndValues() {
        java.util.LinkedHashMap<String, Object> sortKeys = new java.util.LinkedHashMap<>();
        sortKeys.put("createTimeMs desc", 1729000000000L);
        sortKeys.put("title", "设计评审\"引用\"与\\反斜杠");
        sortKeys.put("flag", Boolean.TRUE);
        sortKeys.put("missing", null);

        String token = tokens.nextToken(623809242866016L, "projectId=\"101\"", sortKeys);

        PageTokens.Cursor cursor = tokens.parseCursor(token, "projectId=\"101\"");
        assertThat(cursor.lastId()).isEqualTo(623809242866016L);
        // 键序保留（LinkedHashMap 签发序）；null 值经 JSON 往返原样还原（Map.entry 禁 null，逐键断言）
        assertThat(cursor.sortKeys().keySet()).containsExactly("createTimeMs desc", "title", "flag", "missing");
        assertThat(cursor.sortKeys().get("createTimeMs desc")).isEqualTo(1729000000000L);
        assertThat(cursor.sortKeys().get("title")).isEqualTo("设计评审\"引用\"与\\反斜杠");
        assertThat(cursor.sortKeys().get("flag")).isEqualTo(Boolean.TRUE);
        assertThat(cursor.sortKeys()).containsKey("missing");
        assertThat(cursor.sortKeys().get("missing")).isNull();
    }

    @Test
    void tokenWithSortKeys_stillParsesByLegacyParse() {
        // 向后兼容：新载荷对既有 parse(String, String) 调用方透明（仅取 lastId）
        java.util.Map<String, Object> sortKeys = java.util.Map.of("createTimeMs desc", 1729000000000L);
        String token = tokens.nextToken(42L, null, sortKeys);
        assertThat(tokens.parse(token, null)).isEqualTo(42L);
    }

    @Test
    void parseCursor_legacyPayloadWithoutSortKeys_returnsEmptySortKeys() {
        String token = tokens.nextToken(7L, null);
        PageTokens.Cursor cursor = tokens.parseCursor(token, null);
        assertThat(cursor.lastId()).isEqualTo(7L);
        assertThat(cursor.sortKeys()).isEmpty();
    }

    @Test
    void nextToken_nullOrEmptySortKeys_payloadIdenticalToLegacy() {
        // null / 空 sortKeys 与旧版载荷逐字节同构（同 IV 才能逐字节比对——用往返等价断言）
        assertThat(tokens.parseCursor(tokens.nextToken(9L, null, null), null).sortKeys()).isEmpty();
        assertThat(tokens.parseCursor(tokens.nextToken(9L, null, java.util.Map.of()), null).lastId()).isEqualTo(9L);
    }

    @Test
    void parseCursor_rejectsTamperedOrMismatchedLikeLegacy() {
        java.util.Map<String, Object> sortKeys = java.util.Map.of("createTimeMs desc", 1729000000000L);
        String token = tokens.nextToken(1L, "status=\"ACTIVE\"", sortKeys);
        // 篡改 → AEAD 认证失败
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThatThrownBy(() -> tokens.parseCursor(tampered, "status=\"ACTIVE\"")).isInstanceOf(DomainException.class)
                .hasMessageContaining("pageToken");
        // filter 漂移 → 400
        assertThatThrownBy(() -> tokens.parseCursor(token, "status=\"DONE\"")).isInstanceOf(DomainException.class)
                .hasMessageContaining("不匹配");
    }

    @Test
    void parseCursor_nullOrBlank_returnsNullLastId() {
        assertThat(tokens.parseCursor(null, null).lastId()).isNull();
        assertThat(tokens.parseCursor(" ", null).sortKeys()).isEmpty();
    }
}
