package com.homefinmate.service;

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
import com.homefinmate.security.JwtUtil;
import com.homefinmate.security.Role;
import com.homefinmate.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// 회원가입/로그인
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final LoginFailureLogRepository loginFailureLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EncryptionUtil encryptionUtil;
    private final AuditService auditService;

    // 회원가입: 이메일 중복 확인 => 비밀번호 해시 => 저장
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Email already registered");
        }

        // 소득(선택) 있으면 AES로 암호화
        String encryptedIncome = null;
        if (request.getIncome() != null) {
            encryptedIncome = encryptionUtil.encrypt(request.getIncome());
        }

        // TODO: ADMIN 계정 생성 방법도 추가할 것
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .encryptedIncome(encryptedIncome)
                .build();

        User saved = userRepository.save(user);

        return SignupResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .role(saved.getRole().name())
                .traceId(request.getTraceId())
                .build();
    }

    // 로그인: 사용자 확인 => 비밀번호 비교 => JWT 발급 (실패 시 실패 기록 저장)
    public LoginResponse login(LoginRequest request) {
        // 없는 이메일: 실패 기록 후 예외
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    loginFailureLogRepository.save(LoginFailureLog.builder()
                            .email(request.getEmail())
                            .reason("User not found")
                            .traceId(request.getTraceId())
                            .build());
                    return new RuntimeException("Invalid credentials");
                });

        // 비밀번호 불일치: 실패 기록 후 예외
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginFailureLogRepository.save(LoginFailureLog.builder()
                    .email(request.getEmail())
                    .reason("Incorrect password")
                    .traceId(request.getTraceId())
                    .build());
            // 이메일/비밀번호 중 무엇이 틀렸는지 알려주지 않음 (계정 존재 여부 노출 방지)
            // 로그인 실패는 401이 아닌 400: 화면의 401 처리(토큰 삭제 후 로그인 화면 이동)와 겹치지 않게
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid credentials");
        }

        // 토큰 발급 (subject=email, roles=[role])
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());

        // 로그인 성공 감사 기록
        auditService.logAudit("USER", user.getEmail(), "LOGIN_SUCCESS", request.getTraceId());

        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .traceId(request.getTraceId())
                .build();
    }
}
