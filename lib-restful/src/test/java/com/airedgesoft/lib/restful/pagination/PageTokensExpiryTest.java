package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 游标有效期单测（审计的回归锚）：载荷含 exp 字段，过期 → 400。
 */
class PageTokensExpiryTest {

    private final PageTokens tokens = new PageTokens(PageTokens.DEFAULT_DEV_SECRET, Duration.ofMinutes(30L));

    @Test
    void parse_expiredToken_rejectedWith400() {
        long past = System.currentTimeMillis() - 1_000L;
        String expired = tokens.nextToken(42L, null, past);
        assertThatThrownBy(() -> tokens.parse(expired, null)).isInstanceOf(DomainException.class)
                .hasMessageContaining("过期").extracting(e -> ((DomainException) e).getType())
                .isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void parse_validToken_withinTtl() {
        long future = System.currentTimeMillis() + 1_000L;
        String token = tokens.nextToken(42L, null, future);
        assertThat(tokens.parse(token, null)).isEqualTo(42L);
    }
}
