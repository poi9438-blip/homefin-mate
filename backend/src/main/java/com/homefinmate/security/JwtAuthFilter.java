package com.homefinmate.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

// 요청마다 traceId를 로그에 넣고 Bearer 토큰이 유효하면 사용자 인증 정보를 등록하는 필터
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        // traceId: 헤더 값을 쓰고 없으면 새로 생성 => 로그(MDC)와 응답 헤더에 넣기
        String traceId = request.getHeader("X-Trace-Id");
        if (!StringUtils.hasText(traceId)) {
            traceId = "hfm-" + UUID.randomUUID();
        }
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            String token = extractToken(request);
            // 유효한 토큰의 이메일로 사용자를 찾아 DB의 역할로 인증 (사용자가 없으면 인증 안 함)
            String email = StringUtils.hasText(token) ? jwtUtil.validEmail(token) : null;
            if (email != null) {
                userRepository.findByEmail(email).ifPresent(user -> {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user.getEmail(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
            filterChain.doFilter(request, response);
        } finally {
            // 스레드 재사용 대비 MDC 정리
            MDC.clear();
        }
    }

    // Authorization 헤더에서 "Bearer " 뒤 토큰만 꺼내기
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
