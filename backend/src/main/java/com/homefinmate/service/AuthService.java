package com.homefinmate.service;

import com.homefinmate.util.EmailHasher;
import com.homefinmate.common.BusinessException;
import org.springframework.http.HttpStatus;
import com.homefinmate.dto.auth.LoginRequest;
import com.homefinmate.dto.auth.LoginResponse;
import com.homefinmate.dto.auth.SignupRequest;
import com.homefinmate.dto.auth.SignupResponse;
import com.homefinmate.entity.LoginFailureLog;
import com.homefinmate.entity.User;
import com.homefinmate.repository.LoginFailureLogRepository;
import com.homefinmate.repository.UserRepository;
import com.homefinmate.security.AdminEmails;
import com.homefinmate.security.JwtUtil;
import com.homefinmate.security.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 회원가입/로그인
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final LoginFailureLogRepository loginFailureLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AdminEmails adminEmails;
    private final AuditService auditService;
    private final EmailHasher emailHasher;

    // 로그인 시도 제한: 15분 안에 5번 실패하면 잠시 막음
    static final int MAX_LOGIN_FAILURES = 5;
    static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    // 시도 횟수로 세는 실패 이유 (막혀서 거절된 시도는 세지 않음)
    static final List<String> COUNTED_FAILURE_REASONS = List.of("User not found", "Incorrect password");
    static final String TOO_MANY_ATTEMPTS_REASON = "Too many attempts";

    // 회원가입: 이메일 중복 확인 => 비밀번호 해시 => 저장
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        // 사용자 저장 (ADMIN_EMAILS에 있는 이메일이면 ADMIN)
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(adminEmails.isAdmin(request.getEmail()) ? Role.ADMIN : Role.USER)
                .build();

        // 동시 가입으로 unique 제약에 걸리면 이메일 중복과 같은 응답
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        return SignupResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .role(saved.getRole().name())
                .traceId(request.getTraceId())
                .build();
    }

    // 로그인: 사용자 확인 => 비밀번호 비교 => JWT 발급 (실패 시 실패 기록 저장)
    public LoginResponse login(LoginRequest request) {
        // 최근 실패가 많으면 비밀번호 확인 전에 막음 (거절된 시도도 기록, 횟수에는 안 셈)
        // 실패 기록에는 이메일 대신 HMAC만 저장하고, 시도 횟수도 HMAC으로 셈
        String emailHash = emailHasher.hash(request.getEmail());
        long recentFailures = loginFailureLogRepository.countByEmailHashAndReasonInAndCreatedAtAfter(
                emailHash, COUNTED_FAILURE_REASONS, LocalDateTime.now().minus(LOGIN_FAILURE_WINDOW));
        if (recentFailures >= MAX_LOGIN_FAILURES) {
            loginFailureLogRepository.save(LoginFailureLog.builder()
                    .emailHash(emailHash)
                    .reason(TOO_MANY_ATTEMPTS_REASON)
                    .traceId(request.getTraceId())
                    .build());
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later");
        }

        // 없는 이메일: 실패 기록 후 예외
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    loginFailureLogRepository.save(LoginFailureLog.builder()
                            .emailHash(emailHash)
                            .reason("User not found")
                            .traceId(request.getTraceId())
                            .build());
                    return new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        // 비밀번호 불일치: 실패 기록 후 예외
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginFailureLogRepository.save(LoginFailureLog.builder()
                    .emailHash(emailHash)
                    .reason("Incorrect password")
                    .traceId(request.getTraceId())
                    .build());
            // 이메일/비밀번호 중 무엇이 틀렸는지 알려주지 않음 (계정 존재 여부 노출 방지)
            // 로그인 실패는 401 (없는 이메일·비밀번호 틀림 모두 같은 응답)
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        // 토큰 발급 (subject=email, roles=[role])
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        // 로그인 성공 감사 기록
        // 감사 기록의 행위자는 이메일 대신 사용자 id (#33)
        auditService.logAudit("USER", "user:" + user.getId(), "LOGIN_SUCCESS", request.getTraceId());

        return LoginResponse.builder()
                .token(token)
                .expiresAt(jwtUtil.expiresAtMillis(token))
                .email(user.getEmail())
                .role(user.getRole().name())
                .traceId(request.getTraceId())
                .build();
    }
}
