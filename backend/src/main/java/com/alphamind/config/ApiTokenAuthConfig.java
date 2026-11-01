package com.alphamind.config;

import com.alphamind.model.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 共享令牌认证门，公网部署前的基础防护。
 *
 * <p>未配置 {@code alphamind.api.token}（环境变量 {@code ALPHAMIND_API_TOKEN}）时完全关闭，
 * 本地开发与现有测试零影响；配置后 {@code /api/**} 全部要求令牌。令牌支持三种携带方式：
 * {@code Authorization: Bearer <token>}、{@code X-API-Token} 头、{@code ?token=} 查询参数
 * （EventSource 无法自定义请求头，SSE 端点只能走查询参数）。
 *
 * <p>这是单用户共享门禁，不是多用户账户体系；watchlist 等处的 {@code userId} 隔离仍按原语义保留。
 */
@Configuration
public class ApiTokenAuthConfig {

    /** OPTIONS 预检不含自定义凭证，直接放行交给 CORS 层。 */
    private static final String PREFLIGHT = "OPTIONS";

    @Bean
    public FilterRegistrationBean<ApiTokenAuthFilter> apiTokenAuthFilter(
            @Value("${alphamind.api.token:}") String token, ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiTokenAuthFilter> registration =
                new FilterRegistrationBean<>(new ApiTokenAuthFilter(token, objectMapper));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(0);
        return registration;
    }

    /** 包可见便于无 Spring 上下文的单测；static 断言与实例无关。 */
    static class ApiTokenAuthFilter extends OncePerRequestFilter {

        private static final Logger log = LoggerFactory.getLogger(ApiTokenAuthFilter.class);

        private final String token;
        private final ObjectMapper objectMapper;

        ApiTokenAuthFilter(String token, ObjectMapper objectMapper) {
            this.token = token == null ? "" : token.trim();
            this.objectMapper = objectMapper;
        }

        boolean enabled() {
            return !token.isEmpty();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (!enabled() || PREFLIGHT.equals(request.getMethod()) || hasValidToken(request)) {
                chain.doFilter(request, response);
                return;
            }
            log.warn("API 请求缺少有效令牌: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    objectMapper.writeValueAsString(ApiResponse.error(401, "缺少或无效的 API 令牌")));
        }

        private boolean hasValidToken(HttpServletRequest request) {
            return matches(headerToken(request)) || matches(request.getParameter("token"));
        }

        private String headerToken(HttpServletRequest request) {
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return authorization.substring(7).trim();
            }
            return request.getHeader("X-API-Token");
        }

        /** 常量时间比较，避免计时侧信道泄漏令牌前缀。 */
        private boolean matches(String candidate) {
            if (candidate == null || candidate.isEmpty()) {
                return false;
            }
            return MessageDigest.isEqual(
                    candidate.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        }
    }
}
