package com.airedgesoft.lib.restful.sunset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link Sunset} 拦截器：对标注端点（方法或类级）下发 {@code Deprecation: true} 与 {@code Sunset: <IMF-fixdate>} （RFC 8594 §2；如
 * {@code Sunset: Wed, 01 Jan 2027 00:00:00 GMT}）。{@code dateTime()} 必须为合法 RFC 3339 时刻； 解析失败在拦截期抛出（请求期
 * 500）——标注值应在评审时静态核对。
 */
public class SunsetInterceptor implements HandlerInterceptor {

    private static final DateTimeFormatter IMF_FIXDATE = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Sunset sunset = AnnotationUtils.getAnnotation(handlerMethod.getMethod(), Sunset.class);
        if (sunset == null) {
            sunset = AnnotationUtils.getAnnotation(handlerMethod.getBeanType(), Sunset.class);
        }
        if (sunset == null) {
            return true;
        }
        response.setHeader("Deprecation", "true");
        response.setHeader("Sunset", IMF_FIXDATE.format(Instant.parse(sunset.dateTime())));
        return true;
    }
}
