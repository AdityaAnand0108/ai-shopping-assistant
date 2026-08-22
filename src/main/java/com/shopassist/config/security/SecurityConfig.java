package com.shopassist.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopassist.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The HTTP security policy.
 *
 * <p>Browsing the catalog stays public, which matches how a real storefront
 * behaves. Anything tied to a person — chat, orders, profile — requires a token.
 *
 * <p>Sessions are stateless: no JSESSIONID, no server-side session store, and
 * CSRF is therefore disabled because there is no ambient credential a cross-site
 * form could ride on. That reasoning holds only while the token travels in an
 * Authorization header; moving it into a cookie would require CSRF protection
 * back on.
 */
@Configuration
@EnableWebSecurity
@EnableScheduling
public class SecurityConfig {

    private static final String[] PUBLIC_GET_PATHS = {
            "/api/info",
            "/api/products",
            "/api/products/**",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Authentication required",
                                        "A valid access token is required for this endpoint."))
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Access denied",
                                        "Your account is not permitted to perform this action.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Writes a plain problem document instead of Spring Security's default
     * empty body, without echoing the underlying exception — an authentication
     * failure should never explain itself in enough detail to be useful.
     */
    private void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws java.io.IOException {

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setType(URI.create("https://shop-assistant.local/problems/authentication"));

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // The Vite and CRA dev servers the Phase 10 frontend will run on.
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
