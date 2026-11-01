package com.alphamind.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ApiTokenAuthConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record Result(MockHttpServletResponse response, MockFilterChain chain) {}

    private static Result run(String configuredToken, MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        ApiTokenAuthConfig.ApiTokenAuthFilter filter =
                new ApiTokenAuthConfig.ApiTokenAuthFilter(configuredToken, MAPPER);
        try {
            filter.doFilter(request, response, chain);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return new Result(response, chain);
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    @Test
    void passesEverythingWhenTokenNotConfigured() {
        MockHttpServletRequest request = request("GET", "/api/v1/stocks/watchlist");

        Result result = run("", request);

        assertEquals(200, result.response().getStatus());
        assertNotNull(result.chain().getRequest(), "未配置令牌时请求必须到达业务链");
    }

    @Test
    void rejectsRequestWithoutCredentialsWhenTokenConfigured() throws Exception {
        Result result = run("secret", request("GET", "/api/v1/analysis/history"));

        assertEquals(401, result.response().getStatus());
        assertNull(result.chain().getRequest(), "未携带令牌的请求不得进入业务链");
        String body = result.response().getContentAsString();
        assertTrue(body.contains("401"));
        assertTrue(body.contains("令牌"));
    }

    @Test
    void acceptsBearerHeaderQueryTokenAndCustomHeader() {
        MockHttpServletRequest bearer = request("GET", "/api/v1/stocks/watchlist");
        bearer.addHeader("Authorization", "Bearer secret");
        assertNotNull(run("secret", bearer).chain().getRequest());

        MockHttpServletRequest bearerCase = request("GET", "/api/v1/stocks/watchlist");
        bearerCase.addHeader("Authorization", "bearer secret");
        assertNotNull(run("secret", bearerCase).chain().getRequest());

        MockHttpServletRequest header = request("GET", "/api/v1/stocks/watchlist");
        header.addHeader("X-API-Token", "secret");
        assertNotNull(run("secret", header).chain().getRequest());

        MockHttpServletRequest query = request("GET", "/api/v1/analysis/stream");
        query.setParameter("token", "secret");
        assertNotNull(run("secret", query).chain().getRequest());
    }

    @Test
    void rejectsWrongTokenAndEmptyBearer() {
        MockHttpServletRequest wrong = request("GET", "/api/v1/stocks/watchlist");
        wrong.addHeader("X-API-Token", "wrong-token");
        assertEquals(401, run("secret", wrong).response().getStatus());

        MockHttpServletRequest emptyBearer = request("GET", "/api/v1/stocks/watchlist");
        emptyBearer.addHeader("Authorization", "Bearer ");
        assertEquals(401, run("secret", emptyBearer).response().getStatus());

        MockHttpServletRequest notBearer = request("GET", "/api/v1/stocks/watchlist");
        notBearer.addHeader("Authorization", "Basic secret");
        assertEquals(401, run("secret", notBearer).response().getStatus());
    }

    @Test
    void optionsPreflightPassesWithoutToken() {
        Result result = run("secret", request("OPTIONS", "/api/v1/analysis/stream"));

        assertEquals(200, result.response().getStatus());
        assertNotNull(result.chain().getRequest());
    }
}
