package com.uptimewatch.web;

import com.uptimewatch.config.UptimeWatchProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The status page is public, but changing what is monitored is not: every
 * non-GET request under /api must carry the configured X-Api-Key header.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    static final String HEADER = "X-Api-Key";

    private final byte[] expectedKey;

    public ApiKeyInterceptor(UptimeWatchProperties properties) {
        this.expectedKey = properties.apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        String provided = request.getHeader(HEADER);
        // Constant-time comparison so the key can't be guessed from response timing.
        if (provided != null && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Missing or invalid X-Api-Key header\"}");
        return false;
    }
}
