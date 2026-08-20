package app.nowtask.config;

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
import app.nowtask.identity.api.ApiKeyAuthenticator;
import app.nowtask.identity.api.AuditLog;
import tools.jackson.databind.ObjectMapper;

@Configuration
class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            ApiKeyAuthenticator apiKeyAuthenticator,
            AuditLog auditLog,
            ObjectMapper objectMapper) throws Exception {

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
                        .requestMatchers("/api/auth/login", "/api/auth/signup", "/api/meta").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/members", "/api/admin/teams",
                                "/api/admin/permissions")
                        .authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/integrations/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(
                        new ApiKeyAuthenticationFilter(apiKeyAuthenticator, errorWriter), AuthorizationFilter.class)
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
