package com.oldnew.identity.config.ratelimit;

import com.oldnew.identity.config.security.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/**
 * Inbound rate limit. Key = token subject when authenticated, otherwise client IP plus the submitted username
 * (so credential stuffing is throttled per account as well as per IP). Over the limit: 429 + Retry-After.
 * <p>
 * Not a Spring bean on purpose: it is added inside the security chains only, never as a servlet filter.
 * Behind a proxy, set server.forward-headers-strategy so getRemoteAddr() is the real client IP.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiter limiter;
    private final ProblemResponseWriter problems;

    public RateLimitFilter(RateLimiter limiter, ProblemResponseWriter problems) {
        this.limiter = limiter;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<RateLimitRule> rule = limiter.ruleFor(request.getMethod(), request.getRequestURI());
        if (rule.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimiter.Decision decision = limiter.tryConsume(rule.get(), callerKey(request));
        if (decision.allowed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
            chain.doFilter(request, response);
            return;
        }
        log.info("ratelimit.rejected rule={} path={}", rule.get().name(), request.getRequestURI());
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests; retry later");
    }

    static String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "sub:" + auth.getName();
        }
        String key = "ip:" + request.getRemoteAddr();
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            key += "|user:" + username.trim().toLowerCase(Locale.ROOT);
        }
        return key;
    }
}
