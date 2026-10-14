package com.oldnew.identity.config.security;

import com.oldnew.identity.config.IdentityProperties;
import com.oldnew.identity.config.ratelimit.RateLimitFilter;
import com.oldnew.identity.config.ratelimit.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Three chains, most specific first; anything not matched explicitly needs authentication.
 * <ol>
 *     <li>OAuth2/OIDC endpoints (/oauth2/**, /.well-known/**, /userinfo...).</li>
 *     <li>Identity's own REST API (/api/**): stateless, bearer JWT, Problem Details on 401/403.</li>
 *     <li>Everything else: the login form, health probes, API docs.</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    private final boolean customerIdentity;

    /** ADR-0009: one image, two instances. Runs before the chains are built, so a wrong setup fails clearly. */
    SecurityConfig(Environment environment) {
        boolean customer = environment.matchesProfiles("customer");
        if (customer == environment.matchesProfiles("staff")) {
            throw new IllegalStateException("Activate exactly one of the profiles 'customer' or 'staff' (ADR-0009)");
        }
        this.customerIdentity = customer;
    }

    @Bean
    RateLimiter rateLimiter(IdentityProperties properties) {
        return new RateLimiter(properties.rateLimits());
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http, RateLimiter limiter, ProblemResponseWriter problems)
            throws Exception {
        // Spring Security 7: the authorization server is configured through HttpSecurity itself.
        http.oauth2AuthorizationServer(authorizationServer -> {
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                // The UIs exchange their code for tokens from JavaScript (PKCE), so these endpoints answer CORS.
                .cors(Customizer.withDefaults())
                // Browsers without a session go to the login page; API clients get 401.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .addFilterAfter(new RateLimitFilter(limiter, problems), SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, RateLimiter limiter, ProblemResponseWriter problems) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(authorize -> {
                    if (customerIdentity) {
                        // Self sign-up exists on the Customer Identity only (SignUpController).
                        authorize.requestMatchers(HttpMethod.POST, "/api/v1/users").permitAll();
                    }
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/admin/users").hasRole("ACCOUNT_CREATOR")
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems.authenticationEntryPoint()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems.authenticationEntryPoint())
                        .accessDeniedHandler(problems.accessDeniedHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless bearer-token API: no cookies, so no CSRF exposure.
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .addFilterAfter(new RateLimitFilter(limiter, problems), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultChain(HttpSecurity http, RateLimiter limiter, ProblemResponseWriter problems) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated())
                // failureUrl makes the login page render its error view at /login?error (without it: 404).
                .formLogin(form -> form.failureUrl("/login?error").withObjectPostProcessor(sameMessageForEveryFailure()))
                .addFilterAfter(new RateLimitFilter(limiter, problems), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * Same message for unknown user, wrong password and locked account: no account enumeration. Spring's own
     * handler would show the exception's message, such as "User account is locked".
     */
    private static ObjectPostProcessor<UsernamePasswordAuthenticationFilter> sameMessageForEveryFailure() {
        SimpleUrlAuthenticationFailureHandler toLoginPage = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends UsernamePasswordAuthenticationFilter> O postProcess(O filter) {
                filter.setAuthenticationFailureHandler((request, response, exception) -> toLoginPage
                        .onAuthenticationFailure(request, response, new BadCredentialsException("Invalid credentials")));
                return filter;
            }
        };
    }

    /**
     * Explicit allowlist, only for what a browser app calls from JavaScript: discovery, JWKS, token, revoke.
     * The login page and /oauth2/authorize are full-page navigations and need no CORS. No cookies are sent.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(IdentityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.cors() == null ? List.of() : properties.cors().allowedOrigins();
        if (!origins.isEmpty()) {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(origins);
            config.setAllowedMethods(List.of("GET", "POST"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            config.setMaxAge(Duration.ofHours(1));
            for (String path : List.of("/.well-known/openid-configuration", "/oauth2/jwks", "/oauth2/token", "/oauth2/revoke")) {
                source.registerCorsConfiguration(path, config);
            }
        }
        return source;
    }

    /** "scope" becomes SCOPE_x authorities; "roles" becomes ROLE_x. */
    static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            authorities.addAll(roles.convert(jwt));
            return authorities;
        });
        return converter;
    }
}
