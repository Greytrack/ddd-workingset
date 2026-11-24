package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 游标分页 Page Token 编解码（v4 共享工具，lib-restful；供各 BC QueryService 注入使用）。
 *
 * <p>
 * <b>不透明 + 加密（对齐原书 Geewax ch21，不做偏离；v4-restful-api.md §7）</b>：内部为 {@code AES-GCM(JSON{lastId, fh, exp[, sk]})} 后
 * Base64Url——客户端<b>不可解码、不可篡改</b>（GCM 认证标签，篡改 / 密钥不符 → 解密失败）：
 * <ul>
 * <li><b>防篡改</b>：AEAD 认证失败 → 400；</li>
 * <li><b>防漂移</b>：filter 指纹（SHA-256 截断，替代可碰撞的 {@code hashCode}）不符 → 400；</li>
 * <li><b>有效期</b>：{@code exp}（签发 + TTL，缺省 30 分钟）过期 → 400。</li>
 * </ul>
 * <b>密钥</b>经配置 {@code airedge.restful.pagination.page-token-secret} 下发，生产必须覆盖默认开发密钥；轮换即更换密钥，既有游标全部失效。 加密密钥由配置密钥经
 * SHA-256 派生（确定性，免密钥文件格式）。
 *
 * <p>
 * <b>可选 sortKeys（复合排序键 seek）</b>：非主键序（orderBy）分页时，光靠 {@code lastId} 无法在有序序列上定位「下一页起点」；
 * {@link #nextToken(Long, String, Map)} 允许携带<b>有序</b>排序键值对（{@code sk} 节点，JSON 对象保序），与 {@code lastId}（主键兜底位） 共同构成复合 seek
 * 键。键名编码 （字段、方向修饰）由调用方（BC QueryService）按自身 orderBy 约定自定，本类只负责<b>透明编解码</b>。 <b>向后兼容</b>：不携带 sortKeys
 * 的载荷与历史完全一致（旧游标、旧代码双向可读）。
 */
public final class PageTokens {

    /** 开发默认密钥：仅本地 / workshop；生产必须经配置覆盖。 */
    public static final String DEFAULT_DEV_SECRET = "airedge-dev-page-token-secret-change-me";

    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SecretKeySpec key;
    private final long ttlMillis;

    /** 构造加密游标编解码器（TTL 即游标有效期，见 {@link PaginationProperties#getPageTokenTtl()}）。 */
    public PageTokens(String secret, Duration ttl) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("page-token-secret 不能为空");
        }
        this.key = deriveKey(secret);
        this.ttlMillis = ttl == null || ttl.isNegative() ? Duration.ofMinutes(30L).toMillis() : ttl.toMillis();
    }

    /** 由最后一条记录的 ID 与过滤条件指纹生成下一页游标；末页返回 null（响应中省略字段）。 */
    public String nextToken(Long lastId, String filter) {
        return nextToken(lastId, filter, Instant.now().toEpochMilli() + ttlMillis);
    }

    /**
     * 复合排序键游标：在 {@link #nextToken(Long, String)} 基础上追加<b>有序</b>排序键值对（{@code sk} 节点）， 供非主键序（orderBy）分页的复合 seek
     * 定位——键名编码（字段、方向修饰）由调用方按自身 orderBy 约定自定。 {@code sortKeys} 为 null / 空时载荷与旧版完全一致（向后兼容，旧游标双向可读）。
     *
     * <p>
     * 值类型支持 JSON 标量（String / Number / Boolean / null）；需保序时请传 {@link LinkedHashMap}。
     */
    public String nextToken(Long lastId, String filter, Map<String, Object> sortKeys) {
        if (sortKeys == null || sortKeys.isEmpty()) {
            return nextToken(lastId, filter);
        }
        if (lastId == null) {
            return null;
        }
        String sk;
        try {
            sk = JSON.writeValueAsString(sortKeys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("pageToken sortKeys 序列化失败", e);
        }
        String payload = "{\"lastId\":" + lastId + ",\"fh\":\"" + filterHash(filter) + "\",\"exp\":"
                + (Instant.now().toEpochMilli() + ttlMillis) + ",\"sk\":" + sk + "}";
        return encrypt(payload);
    }

    /** 指定过期时间的内部重载（供测试构造过期 token；生产方走上面的公开入口）。 */
    String nextToken(Long lastId, String filter, long expiresAtMillis) {
        if (lastId == null) {
            return null;
        }
        String payload = "{\"lastId\":" + lastId + ",\"fh\":\"" + filterHash(filter) + "\",\"exp\":" + expiresAtMillis
                + "}";
        return encrypt(payload);
    }

    /** 校验认证、有效期与 filter 指纹并解析游标；非法 / 篡改 / 过期 / filter 不符 → 400。 */
    public Long parse(String token, String filter) {
        return parseCursor(token, filter).lastId();
    }

    /**
     * 复合游标解析：在 {@link #parse(String, String)} 校验链（认证 / 有效期 / filter 指纹，违规 → 400）之上返回完整 {@link Cursor} —— 旧载荷（无
     * {@code sk} 节点）的 {@code sortKeys} 为空映射，行为与历史一致。
     */
    public Cursor parseCursor(String token, String filter) {
        if (token == null || token.isBlank()) {
            return new Cursor(null, Map.of());
        }
        String json = decrypt(token);
        Map<String, Object> payload;
        try {
            payload = JSON.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IllegalArgumentException | JsonProcessingException e) {
            // 同密钥加密的载荷 JSON 形态非法：按客户端篡改/损坏处理（解密失败已在 decrypt 抛 400 TAMPERED）
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        Object lastId = payload.get("lastId");
        Object exp = payload.get("exp");
        if (!(lastId instanceof Number) || !(exp instanceof Number)) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        if (((Number) exp).longValue() < Instant.now().toEpochMilli()) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_EXPIRED);
        }
        String expectedFh = payload.get("fh") == null ? "" : String.valueOf(payload.get("fh"));
        if (!expectedFh.equals(filterHash(filter))) {
            // 过滤条件指纹不一致：防止翻页期间偷换 filter 造成数据流错乱
            throw new DomainException(DomainRestCode.PAGE_TOKEN_FILTER_MISMATCH);
        }
        Map<String, Object> sortKeys = payload.get("sk") instanceof Map<?, ?> sk ? stringKeyed(sk) : Map.of();
        return new Cursor(((Number) lastId).longValue(), sortKeys);
    }

    /** JSON 对象 → 有序 {@code Map<String, Object>}（保留键序；非法键形态按损坏游标处理 → 400）。 */
    private static Map<String, Object> stringKeyed(Map<?, ?> raw) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
            }
            ordered.put(key, entry.getValue());
        }
        return ordered;
    }

    /**
     * 解析后的游标：{@code lastId} = 最后一条记录主键（seek 主键兜底位）；{@code sortKeys} = 可选的有序排序键值对 （旧载荷为空映射）。
     *
     * @param lastId
     *            最后一条记录 ID；null = 首页（未携带游标）
     * @param sortKeys
     *            有序排序键值对（不可变；键序 = 签发时的写入序）
     */
    public record Cursor(Long lastId, Map<String, Object> sortKeys) {
        public Cursor {
            sortKeys = sortKeys == null
                    ? Map.of()
                    : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sortKeys));
        }
    }

    /** AES-GCM 加密：随机 12 字节 IV + 密文，Base64Url(IV‖密文)。 */
    private String encrypt(String payload) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("pageToken 加密失败", e);
        }
    }

    /**
     * AES-GCM 解密。非法编码 / 认证失败（篡改、密钥不符）→ 400；密钥或算法配置错误属服务端故障 → 500（IllegalStateException），不伪装成客户端错误。
     */
    private String decrypt(String token) {
        byte[] combined;
        try {
            combined = Base64.getUrlDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        if (combined.length < IV_LENGTH + GCM_TAG_LENGTH_BITS / 8) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_INVALID);
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new DomainException(DomainRestCode.PAGE_TOKEN_TAMPERED);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("pageToken 加密配置错误（密钥/算法）", e);
        }
    }

    /** filter 指纹：SHA-256 前 8 字节十六进制（替代 32 位可碰撞的 {@code String.hashCode}）。 */
    static String filterHash(String filter) {
        if (filter == null || filter.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(filter.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    /** 密钥：配置密钥经 SHA-256 派生为 256 位 AES 密钥（确定性，无需密钥文件格式）。 */
    private static SecretKeySpec deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new SecretKeySpec(digest.digest(secret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException("page-token-secret 派生失败", e);
        }
    }
}
