package com.delena.tradingportal.security;

import com.delena.tradingportal.config.TradingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Resource-server security. Health endpoints are always public. Default path validates CSS JWTs
 * via JWKS (audience/client = trading-portal). When {@code trading.security.dev-bypass=true}, a
 * fixed-header dev filter is used instead (local engine testing only; CSS unreachable in DEV).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String[] PUBLIC = {
            "/api/health", "/api/health/ny-time", "/actuator/health", "/error"
    };

    @Bean
    @ConditionalOnProperty(name = "trading.security.dev-bypass", havingValue = "true")
    public SecurityFilterChain devBypassFilterChain(HttpSecurity http, TradingProperties props) throws Exception {
        log.warn("SECURITY: dev-bypass ENABLED — fixed-header auth (X-Dev-Token). DEV ONLY. "
                + "CSS JWKS validation is disabled while this flag is true.");
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .anonymous(a -> a.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new DevBypassAuthFilter(props), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "trading.security.dev-bypass", havingValue = "false", matchIfMissing = true)
    public SecurityFilterChain jwksFilterChain(HttpSecurity http, TradingProperties props) throws Exception {
        log.info("SECURITY: CSS JWKS resource server enabled (jwkSetUri={}, clientId={})",
                props.getSecurity().getJwkSetUri(), props.getSecurity().getClientId());
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(jwtDecoder(props))));
        return http.build();
    }

    /** Lazy JWKS decoder (no network at build time) with audience = clientId validation. */
    private JwtDecoder jwtDecoder(TradingProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.getSecurity().getJwkSetUri()).build();
        String clientId = props.getSecurity().getClientId();
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            boolean audMatch = aud != null && aud.contains(clientId);
            boolean clientMatch = clientId.equals(jwt.getClaimAsString("client_id"))
                    || clientId.equals(jwt.getClaimAsString("azp"));
            if (audMatch || clientMatch) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "Required audience/client " + clientId + " not present", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), audience));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        // Angular DEV UI (:3341) + localhost dev hosts.
        c.setAllowedOriginPatterns(List.of(
                "http://localhost:3341", "http://127.0.0.1:3341",
                "http://localhost:*", "http://127.0.0.1:*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
