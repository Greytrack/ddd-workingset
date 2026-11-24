package com.airedgesoft.lib.restful.sunset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Sunset / Deprecation 响应头拦截器单测（组件 13；RFC 8594 IMF-fixdate 形态）。
 */
class SunsetInterceptorTest {

    private final SunsetInterceptor interceptor = new SunsetInterceptor();

    @Test
    void annotatedMethod_emitsDeprecationAndSunsetHeaders() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        interceptor.preHandle(mock(HttpServletRequest.class), response, handler("sunsetEndpoint"));
        verify(response).setHeader("Deprecation", "true");
        verify(response).setHeader("Sunset", "Thu, 01 Jan 2026 00:00:00 GMT");
    }

    @Test
    void classLevelAnnotation_inheritedByAllEndpoints() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        interceptor.preHandle(mock(HttpServletRequest.class), response, handler("plainEndpoint"));
        verify(response).setHeader("Deprecation", "true");
        verify(response).setHeader("Sunset", "Sat, 01 Jan 2028 00:00:00 GMT");
    }

    @Test
    void nonAnnotatedHandler_noHeaders() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        boolean proceed = interceptor.preHandle(mock(HttpServletRequest.class), response, handler("cleanEndpoint"));
        assertThat(proceed).isTrue();
        verifyNoInteractions(response);
    }

    @Test
    void nonHandlerMethod_noHeaders() {
        HttpServletResponse response = mock(HttpServletResponse.class);
        boolean proceed = interceptor.preHandle(mock(HttpServletRequest.class), response, new Object());
        assertThat(proceed).isTrue();
        verifyNoInteractions(response);
    }

    @Test
    void imfFixdate_matchesRfc8594() {
        // HTTP-date 必须为 IMF-fixdate（RFC 9110 §5.6.7），供客户端 Date 解析
        String httpDate = java.time.format.DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US).withZone(java.time.ZoneOffset.UTC)
                .format(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(httpDate).isEqualTo("Thu, 01 Jan 2026 00:00:00 GMT");
        assertThat(LocalDate.parse("2026-01-01").toString()).isEqualTo("2026-01-01");
    }

    // ── 样例 Controller ──

    @Sunset(dateTime = "2028-01-01T00:00:00Z")
    static class SunsetHolder {

        @SuppressWarnings("unused")
        void plainEndpoint() {
        }
    }

    static class CleanHolder {

        @SuppressWarnings("unused")
        void cleanEndpoint() {
        }
    }

    static class MixedHolder {

        @Sunset(dateTime = "2026-01-01T00:00:00Z")
        @SuppressWarnings("unused")
        void sunsetEndpoint() {
        }
    }

    private HandlerMethod handler(String method) throws Exception {
        return switch (method) {
            case "plainEndpoint" -> handlerOf(SunsetHolder.class, method);
            case "cleanEndpoint" -> handlerOf(CleanHolder.class, method);
            default -> handlerOf(MixedHolder.class, method);
        };
    }

    private HandlerMethod handlerOf(Class<?> beanType, String method)
            throws NoSuchMethodException, ReflectiveOperationException {
        return new HandlerMethod(beanType.getDeclaredConstructor().newInstance(), beanType.getDeclaredMethod(method));
    }
}
