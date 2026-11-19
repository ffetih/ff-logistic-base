package com.siose.ff.base.security.autoconfigure;

import com.siose.ff.base.security.TenantSecurityProperties;
import com.siose.ff.base.security.TenantContextFilter;
import com.siose.ff.base.security.TenantJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Stateless, multi-tenant resource-server security for every product built on base.
 *
 * <p>Access tokens are verified locally from the configured JWKS (signature, expiry, issuer,
 * audience); products point that JWKS at the identity service and never call Keycloak.
 * Role checks are declared with {@code @PreAuthorize}. Runs before Spring Boot's own web-security
 * auto-configurations, which back off once this filter chain exists. A product can replace it
 * by declaring its own {@link SecurityFilterChain}.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
})
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(TenantSecurityProperties.class)
public class TenantSecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantSecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, TenantSecurityProperties properties)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        .anyRequest().authenticated());

        if (properties.getHeaderFallback().isEnabled()) {
            log.warn("ff.security.header-fallback.enabled=true: JWT verification is off and "
                    + "X-Tenant-Id is accepted without a token. Local development only.");
            http.anonymous(AbstractHttpConfigurer::disable)
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .addFilterBefore(new TenantContextFilter(properties), AuthorizationFilter.class);
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                            new TenantJwtAuthenticationConverter(properties.getTenantClaim()))))
                    .addFilterBefore(new TenantContextFilter(properties), AuthorizationFilter.class);
        }

        return http.build();
    }
}
