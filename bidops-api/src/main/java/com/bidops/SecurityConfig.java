package com.bidops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JWT Stateless 인증 골격.
 *
 * ── 범위 ────────────────────────────────────────────────────────────────────
 * 이 클래스는 Security 설정 골격만 유지한다.
 * auth 관련 API(/auth/login, /auth/refresh 등)는 이 파일에 추가하지 않는다.
 *   → auth 상세는 별도 AuthController + JwtAuthFilter 로 분리할 것 (TODO)
 *
 * ── 추가 예정 (TODO) ─────────────────────────────────────────────────────────
 * 1. JwtAuthFilter 구현 후 addFilterBefore() 등록
 * 2. JwtTokenProvider 구현 (서명 검증, 클레임 추출)
 * 3. UserDetailsService 연동 (projectId 기반 접근 제어 포함)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        List<String> paths = new ArrayList<>(Arrays.asList(PUBLIC_PATHS));

        // local/dev 프로파일에서만 전체 API 인증 없이 허용
        if ("local".equals(activeProfile) || "dev".equals(activeProfile)) {
            paths.add("/api/**");
        }

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s ->
                    s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(paths.toArray(String[]::new)).permitAll()
                    .anyRequest().authenticated()
            );

        // TODO: JWT 필터 활성화
        // http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
