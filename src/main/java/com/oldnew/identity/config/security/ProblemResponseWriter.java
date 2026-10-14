package com.oldnew.identity.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Writes RFC 9457 problems from filters, where Spring MVC's advice cannot reach
 * (401, 403 decided by the security chain, and 429 from the rate limiter).
 * Values are fixed strings from this codebase, so the minimal JSON escaping below is enough.
 */
@Component
public class ProblemResponseWriter {

    public void write(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        StringBuilder json = new StringBuilder(256)
                .append("{\"type\":\"urn:oldnew:problem:").append(code.toLowerCase(Locale.ROOT).replace('_', '-')).append('"')
                .append(",\"title\":\"").append(escape(status.getReasonPhrase())).append('"')
                .append(",\"status\":").append(status.value())
                .append(",\"detail\":\"").append(escape(detail)).append('"')
                .append(",\"code\":\"").append(code).append('"');
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            json.append(",\"traceId\":\"").append(escape(traceId)).append('"');
        }
        json.append('}');
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.toString());
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid bearer token is required");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) ->
                write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission for this operation");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
