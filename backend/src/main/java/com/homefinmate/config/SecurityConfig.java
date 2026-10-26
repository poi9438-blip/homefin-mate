package com.homefinmate.config;

import org.springframework.beans.factory.annotation.Value;
import com.homefinmate.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import com.homefinmate.security.AuthCookies;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
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
                // CSRF: 로그인 쿠키로 인증하는 상태 변경 요청(POST 등)만 XSRF 토큰 확인
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(SecurityConfig::needsCsrfCheck)
                        // 인증될 때 CSRF 토큰을 지우지 않음 (요청마다 JWT 인증이라)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)

                // CORS 켜기 (아래 corsConfigurationSource의 허용 목록 사용)
                .cors(Customizer.withDefaults())

                // JWT 기반 인증을 사용해서 서버 세션을 사용하지 않는 구조 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 주소별 규칙 (순서대로 적용)
                .authorizeHttpRequests(auth -> auth
                        // 현재 사용자 조회는 로그인 필요, 나머지 인증 API(가입·로그인)는 공개
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
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

    // XSRF-TOKEN 쿠키 (화면이 읽어야 해서 HttpOnly 아님, 모든 경로에서 읽도록 Path=/)
    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(c -> c.path("/").sameSite("Strict"));
        return repository;
    }

    // CSRF 확인 대상: 상태를 바꾸는 메서드이고, 로그인 쿠키가 있고, Authorization 헤더는 없는 요청
    static boolean needsCsrfCheck(HttpServletRequest request) {
        boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
        return unsafe && AuthCookies.read(request) != null && !StringUtils.hasText(request.getHeader("Authorization"));
    }

    // 모든 응답에 XSRF-TOKEN 쿠키가 있도록 토큰을 미리 꺼내는 필터
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
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
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
