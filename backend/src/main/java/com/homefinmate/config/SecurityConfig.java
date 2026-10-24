package com.homefinmate.config;

import org.springframework.beans.factory.annotation.Value;
import com.homefinmate.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// 보안 설정: 공개 주소, 세션 미사용(JWT), 401/403 JSON 응답, 비밀번호 해시, CORS
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 요청마다 토큰을 확인하는 JWT 필터
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF 보호 끄기
                .csrf(AbstractHttpConfigurer::disable)

                .cors(cors -> {
                })

                // JWT 기반 인증을 사용해서 서버 세션을 사용하지 않는 구조 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 주소별 규칙 (순서대로 적용)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // 정책 API는 로그인 없이 허용
                        .requestMatchers("/api/policy/**").permitAll()
                        // 지역 분석 API는 로그인 없이 허용
                        .requestMatchers("/api/analysis/**").permitAll()
                        // DTI 단독 계산은 로그인 없이 허용
                        .requestMatchers("/api/loan/dti").permitAll()
                        // 관리자 API는 ADMIN만
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/api-docs/**", "/api-docs",
                                "/webjars/**")
                        .permitAll()
                        .anyRequest().authenticated())

                // 여기는 Controller 전 단계라 따로 처리
                .exceptionHandling(ex -> ex
                        // 401: 누구인지 모름 = 인증 실패
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Unauthorized\"}");
                        })
                        // 403: 권한이 없음 = 인가 실패
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Access denied\"}");
                        }))

                // JWT 필터를 기본 로그인 필터 앞에 추가
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // 비밀번호 해시 (BCrypt)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS: 설정한 출처(CORS_ALLOWED_ORIGINS)만 허용, 쿠키 전송 허용 안 함
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
