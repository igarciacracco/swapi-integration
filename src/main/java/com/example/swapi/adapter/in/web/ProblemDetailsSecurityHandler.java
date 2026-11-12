package com.example.swapi.adapter.in.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes 401 and 403 as RFC 9457 problem+json. Spring Security rejects these requests in its
 * filter chain, before {@link ApiExceptionHandler} can see them. Spring's bearer-token handlers
 * still run first, so the standard {@code WWW-Authenticate} challenge is kept.
 */
public class ProblemDetailsSecurityHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String FORBIDDEN_TYPE = "urn:swapi:error:forbidden";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AuthenticationEntryPoint bearerChallenge =
            new BearerTokenAuthenticationEntryPoint();
    private final AccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException, ServletException {
        bearerChallenge.commence(request, response, e);
        // Says whether a token was sent, never why it failed (signature, expiry, issuer).
        String detail = e instanceof OAuth2AuthenticationException
                ? "The bearer token is invalid or expired."
                : "A bearer token is required.";
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiExceptionHandler.UNAUTHORIZED_TYPE.toString(), "Authentication required",
                detail);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException, ServletException {
        bearerDenied.handle(request, response, e);
        write(request, response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_TYPE,
                "Access denied", "The token does not allow this request.");
    }

    private static void write(HttpServletRequest request, HttpServletResponse response, int status,
                              String type, String title, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JSON.writeValue(response.getOutputStream(), body);
    }
}
