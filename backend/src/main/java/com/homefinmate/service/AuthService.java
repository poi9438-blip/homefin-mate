package com.homefinmate.service;

import com.homefinmate.dto.auth.SignupRequest;
import com.homefinmate.dto.auth.SignupResponse;
import com.homefinmate.entity.User;
import com.homefinmate.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final EncryptionUtil encryptionUtil;

    // 회원가입: 이메일 중복 확인 => 비밀번호 해시 => 저장
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
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
}
