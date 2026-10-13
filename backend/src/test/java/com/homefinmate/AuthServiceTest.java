package com.homefinmate;

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
import com.homefinmate.service.AuthService;
import com.homefinmate.util.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// AuthService 단위 테스트 (의존 객체는 Mockito 가짜로 대체)
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserRepository userRepository;
    @Mock
    LoginFailureLogRepository loginFailureLogRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtUtil jwtUtil;
    @Mock
    EncryptionUtil encryptionUtil;
    @InjectMocks
    AuthService authService;

    @Test
    void signup_newUser_savesAndReturnsResponse() {
        SignupRequest req = new SignupRequest();
        req.setEmail("user@test.com");
        req.setPassword("password123");
        req.setIncome("5000");
        req.setTraceId("trace-001");

        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(encryptionUtil.encrypt("5000")).thenReturn("encrypted-income");

        User saved = User.builder()
                .id(1L).email("user@test.com").role(Role.USER).password("hashed").build();
        when(userRepository.save(any())).thenReturn(saved);

        SignupResponse response = authService.signup(req);

        assertThat(response.getEmail()).isEqualTo("user@test.com");
        assertThat(response.getRole()).isEqualTo("USER");
        verify(encryptionUtil).encrypt("5000");
    }

    @Test
    void signup_duplicateEmail_throwsException() {
        SignupRequest req = new SignupRequest();
        req.setEmail("existing@test.com");
        req.setPassword("pass");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void signup_savedUser_hasHashedPasswordAndUserRole() {
        SignupRequest req = new SignupRequest();
        req.setEmail("user@test.com");
        req.setPassword("password123");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password123");
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        authService.signup(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User toSave = captor.getValue();

        assertThat(toSave.getPassword()).isEqualTo("hashed-password123").isNotEqualTo("password123");
        assertThat(toSave.getRole()).isEqualTo(Role.USER);
        assertThat(toSave.getEncryptedIncome()).isNull();
        verify(encryptionUtil, never()).encrypt(anyString());
    }

    // 로그인 성공: 토큰/이메일/역할 반환, 실패 기록 없음
    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("password123");
        req.setTraceId("trace-002");

        User user = User.builder()
                .id(1L).email("user@test.com").password("hashed").role(Role.USER).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("user@test.com", "USER")).thenReturn("jwt-token");

        LoginResponse response = authService.login(req);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getEmail()).isEqualTo("user@test.com");
        assertThat(response.getRole()).isEqualTo("USER");
        verify(loginFailureLogRepository, never()).save(any());
    }

    // 비밀번호 틀림: 실패 기록 저장 + 예외, 토큰 발급 안 함
    @Test
    void login_wrongPassword_logsFailureAndThrows() {
        LoginRequest req = new LoginRequest();
        req.setEmail("user@test.com");
        req.setPassword("wrongpass");
        req.setTraceId("trace-003");

        User user = User.builder()
                .id(1L).email("user@test.com").password("hashed").role(Role.USER).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid credentials");

        ArgumentCaptor<LoginFailureLog> captor = ArgumentCaptor.forClass(LoginFailureLog.class);
        verify(loginFailureLogRepository).save(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("Incorrect password");
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-003");
        verify(jwtUtil, never()).generateToken(anyString(), anyString());
    }

    // 없는 이메일: 실패 기록 저장 + 비밀번호 틀림과 같은 메시지
    @Test
    void login_unknownEmail_logsFailureWithSameMessage() {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody@test.com");
        req.setPassword("password123");

        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");

        ArgumentCaptor<LoginFailureLog> captor = ArgumentCaptor.forClass(LoginFailureLog.class);
        verify(loginFailureLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("nobody@test.com");
        assertThat(captor.getValue().getReason()).isEqualTo("User not found");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
