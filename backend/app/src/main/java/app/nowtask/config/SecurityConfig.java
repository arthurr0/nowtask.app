package app.nowtask.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import app.nowtask.identity.api.ApiKeyAuthenticator;
import app.nowtask.identity.api.AuditLog;
import app.nowtask.identity.api.Organizations;
import app.nowtask.identity.api.SessionTracking;
import app.nowtask.identity.api.UserDirectory;
import tools.jackson.databind.ObjectMapper;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            ApiKeyAuthenticator apiKeyAuthenticator,
            AuditLog auditLog,
            Organizations organizations,
            SessionTracking sessionTracking,
            UserDirectory directory,
            ObjectMapper objectMapper,
            CookieSerializer cookieSerializer,
            @Value("${server.servlet.session.timeout:30m}") Duration sessionTimeout) throws Exception {

        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        csrfHandler.setCsrfRequestAttributeName(null);

        ApiErrorWriter errorWriter = new ApiErrorWriter(objectMapper);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler)
                        .ignoringRequestMatchers(request -> ApiKeyAuthenticationFilter.bearerToken(request) != null))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/signup", "/api/auth/verify-email",
                                "/api/auth/confirm-email-change", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/meta")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/invites/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/invites/*/accept",
                                "/api/invites/*/request-new")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/organization/members", "/api/organization/teams",
                                "/api/organization/permissions")
                        .authenticated()
                        .requestMatchers("/api/orgs", "/api/orgs/**").authenticated()
                        .requestMatchers("/api/organization/roles/**").hasAuthority("PERM_ROLES_MANAGE")
                        .requestMatchers("/api/organization/invites", "/api/organization/invites/**")
                        .hasAuthority("PERM_MEMBERS_INVITE")
                        .requestMatchers(HttpMethod.GET, "/api/organization/audit").hasAuthority("PERM_AUDIT_READ")
                        .requestMatchers("/api/organization/**").hasAuthority("PERM_MEMBERS_MANAGE")
                        .requestMatchers("/api/integrations/**").hasAuthority("PERM_INTEGRATIONS_MANAGE")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(new RateLimitFilter(errorWriter), AuthorizationFilter.class)
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyAuthenticator, errorWriter), AuthorizationFilter.class)
                .addFilterBefore(
                        new SessionActivityFilter(sessionTracking, errorWriter), AuthorizationFilter.class)
                .addFilterBefore(
                        new SessionCookieRefreshFilter(cookieSerializer, sessionTimeout), AuthorizationFilter.class)
                .addFilterBefore(
                        new OrganizationContextFilter(organizations, directory, errorWriter),
                        AuthorizationFilter.class)
                .addFilterAfter(new ApiKeyGuardFilter(errorWriter, auditLog), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
