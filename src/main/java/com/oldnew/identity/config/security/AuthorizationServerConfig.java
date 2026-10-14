package com.oldnew.identity.config.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.oldnew.identity.adapter.in.security.AccountPrincipal;
import com.oldnew.identity.config.IdentityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Token signing and token contents. Registered clients and token lifetimes come from
 * {@code spring.security.oauth2.authorizationserver.*} in application.yml (moved to MongoDB in Sprint 2).
 */
@Configuration(proxyBeanMethods = false)
class AuthorizationServerConfig {

    @Bean
    SigningKeys signingKeys(IdentityProperties properties) {
        IdentityProperties.Jwt jwt = properties.jwt();
        return SigningKeys.load(jwt.keysDir(), jwt.activeKeyId(), jwt.allowEphemeralKeys());
    }

    /** Published at /oauth2/jwks (public parts only) and used to sign. */
    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeys keys) {
        return new ImmutableJWKSet<>(keys.jwkSet());
    }

    /** Verifies Identity's own tokens for /api/** in-process, without an HTTP call to itself. */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource,
                          @Value("${spring.security.oauth2.authorizationserver.issuer}") String issuer) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, jwkSource));
        processor.setJWTClaimsSetVerifier((claims, context) -> {
            // Spring's validators below check exp, nbf and iss.
        });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Shapes every token: ES256 with the active kid; "sub" is the stable user id (never the e-mail);
     * "roles" for users; "aud" derived from the granted scopes so each service accepts only tokens meant for it.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(SigningKeys keys, IdentityProperties properties) {
        Map<String, String> audienceByScopePrefix = properties.token().audienceByScopePrefix();
        return context -> {
            context.getJwsHeader().algorithm(SignatureAlgorithm.ES256).keyId(keys.activeKeyId());

            AccountPrincipal user = accountOf(context.getPrincipal());
            boolean accessToken = OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType());
            boolean idToken = OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue());

            if (user != null && (accessToken || idToken)) {
                context.getClaims().subject(user.userId());
            }
            if (accessToken) {
                context.getClaims().audience(audiences(context.getAuthorizedScopes(),
                        context.getRegisteredClient().getClientId(), audienceByScopePrefix));
                if (user != null) {
                    context.getClaims().claim("roles", user.roleNames());
                }
            }
        };
    }

    static List<String> audiences(Set<String> scopes, String clientId, Map<String, String> audienceByScopePrefix) {
        List<String> audiences = new ArrayList<>();
        audiences.add(clientId);
        for (String scope : scopes) {
            int colon = scope.indexOf(':');
            if (colon > 0) {
                String audience = audienceByScopePrefix.get(scope.substring(0, colon));
                if (audience != null && !audiences.contains(audience)) {
                    audiences.add(audience);
                }
            }
        }
        return audiences;
    }

    private static AccountPrincipal accountOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal;
        }
        return null;
    }
}
