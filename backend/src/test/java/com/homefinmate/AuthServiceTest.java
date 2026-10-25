package com.homefinmate;

import com.homefinmate.util.EmailHasher;
import org.mockito.Spy;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDateTime;
import static org.mockito.ArgumentMatchers.eq;
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
import com.homefinmate.service.AuditService;
import com.homefinmate.service.AuthService;
import com.homefinmate.util.EncryptionUtil;
import org.junit.jupiter.api.DisplayName;
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
        // 감사 기록 서비스 (가짜)
        @Mock
        AuditService auditService;
        // 이메일 해시는 실제 객체 (같은 이메일 → 같은 값인지 확인하려고)
        @Spy
        EmailHasher emailHasher = new EmailHasher("HomeFin-AES-256-Key-Test-Padded!");
        @InjectMocks
        AuthService authService;

        @Test
        @DisplayName("[TC-AUTH-001] 회원가입: 이메일·USER 역할 반환, 소득은 암호화")
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
        @DisplayName("[TC-AUTH-002] 이미 가입된 이메일이면 예외")
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
        @DisplayName("[TC-AUTH-003] 비밀번호는 해시로 저장, 역할 USER, 소득 없으면 암호화 안 함")
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

        // 로그인 성공: 토큰/이메일/역할 반환, 감사 기록 저장, 실패 기록 없음
        @Test
        @DisplayName("[TC-AUTH-004] 로그인 성공: 토큰 발급, 감사 기록, 실패 기록 없음")
        void login_validCredentials_returnsTokenAndLogsAudit() {
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
                // 감사 기록의 행위자는 이메일이 아니라 사용자 id (#33)
                verify(auditService).logAudit("USER", "user:1", "LOGIN_SUCCESS", "trace-002");
                verify(loginFailureLogRepository, never()).save(any());
        }

        // 비밀번호 틀림: 실패 기록 저장 + 예외, 토큰 발급 안 함
        @Test
        @DisplayName("[TC-AUTH-005] 비밀번호 틀림: 실패 기록 + 예외, 토큰 발급 안 함")
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
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Invalid credentials")
                                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

                ArgumentCaptor<LoginFailureLog> captor = ArgumentCaptor.forClass(LoginFailureLog.class);
                verify(loginFailureLogRepository).save(captor.capture());
                assertThat(captor.getValue().getReason()).isEqualTo("Incorrect password");
                assertThat(captor.getValue().getTraceId()).isEqualTo("trace-003");
                verify(jwtUtil, never()).generateToken(anyString(), anyString());
                // 실패하면 성공 감사 기록은 남지 않음
                verifyNoInteractions(auditService);
        }

        // 없는 이메일: 실패 기록 저장 + 비밀번호 틀림과 같은 메시지
        @Test
        @DisplayName("[TC-AUTH-006] 없는 이메일: 비밀번호 틀림과 같은 메시지 (계정 존재 여부 숨김)")
        void login_unknownEmail_logsFailureWithSameMessage() {
                LoginRequest req = new LoginRequest();
                req.setEmail("nobody@test.com");
                req.setPassword("password123");

                when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> authService.login(req))
                                .isInstanceOf(BusinessException.class)
                                .hasMessage("Invalid credentials")
                                .extracting("status").isEqualTo(HttpStatus.UNAUTHORIZED);

                ArgumentCaptor<LoginFailureLog> captor = ArgumentCaptor.forClass(LoginFailureLog.class);
                verify(loginFailureLogRepository).save(captor.capture());
                // 실패 기록에는 이메일 대신 HMAC (#33)
                assertThat(captor.getValue().getEmailHash()).isEqualTo(emailHasher.hash("nobody@test.com"))
                                .startsWith("hmac:").doesNotContain("nobody");
                assertThat(captor.getValue().getReason()).isEqualTo("User not found");
                verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        private LoginRequest loginRequest(String email, String password) {
                LoginRequest req = new LoginRequest();
                req.setEmail(email);
                req.setPassword(password);
                req.setTraceId("trace-lock");
                return req;
        }

        // 최근 15분 실패 5번이면 429, 거절도 "Too many attempts"로 기록, 비밀번호 확인 안 함
        @Test
        @DisplayName("[TC-AUTH-015] 최근 15분 실패가 5번이면 429 (비밀번호 확인 전에 거절, 거절도 기록)")
        void login_tooManyRecentFailures_returns429() {
                when(loginFailureLogRepository.countByEmailHashAndReasonInAndCreatedAtAfter(
                                eq(emailHasher.hash("user@test.com")), any(), any()))
                                .thenReturn(5L);

                assertThatThrownBy(() -> authService.login(loginRequest("user@test.com", "password123")))
                                .isInstanceOf(BusinessException.class)
                                .hasMessageContaining("Too many login attempts")
                                .extracting("status").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

                ArgumentCaptor<LoginFailureLog> captor = ArgumentCaptor.forClass(LoginFailureLog.class);
                verify(loginFailureLogRepository).save(captor.capture());
                assertThat(captor.getValue().getReason()).isEqualTo("Too many attempts");
                verifyNoInteractions(userRepository, passwordEncoder, jwtUtil);
        }

        // 4번까지는 평소처럼 확인 (기준: 15분 전 이후, 센 이유는 없는 이메일, 비밀번호 틀림)
        @Test
        @DisplayName("[TC-AUTH-016] 최근 실패가 4번이면 평소처럼 로그인 확인 (15분 기준, 센 이유 2가지)")
        void login_belowLimit_checksPasswordAsUsual() {
                when(loginFailureLogRepository.countByEmailHashAndReasonInAndCreatedAtAfter(
                                eq(emailHasher.hash("user@test.com")), any(), any()))
                                .thenReturn(4L);
                User user = User.builder().id(1L).email("user@test.com").password("hashed").role(Role.USER).build();
                when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
                when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
                when(jwtUtil.generateToken("user@test.com", "USER")).thenReturn("jwt");

                LocalDateTime before = LocalDateTime.now();
                assertThat(authService.login(loginRequest("user@test.com", "password123")).getToken()).isEqualTo("jwt");

                ArgumentCaptor<java.util.Collection<String>> reasons = ArgumentCaptor
                                .forClass(java.util.Collection.class);
                ArgumentCaptor<LocalDateTime> after = ArgumentCaptor.forClass(LocalDateTime.class);
                verify(loginFailureLogRepository).countByEmailHashAndReasonInAndCreatedAtAfter(
                                eq(emailHasher.hash("user@test.com")), reasons.capture(), after.capture());
                assertThat(reasons.getValue()).containsExactlyInAnyOrder("User not found", "Incorrect password");
                assertThat(after.getValue()).isBetween(before.minusMinutes(15).minusSeconds(5),
                                before.minusMinutes(15).plusSeconds(5));
        }

        // 저장 중 unique 위반이면 이메일 중복과 같은 업무 오류(400)
        @Test
        @DisplayName("[TC-AUTH-017] 동시 가입으로 저장 중 unique 위반이면 400 \"Email already registered\" (500 아님)")
        void signup_uniqueViolationOnSave_returnsDuplicateEmail() {
                SignupRequest req = new SignupRequest();
                req.setEmail("race@test.com");
                req.setPassword("password123");
                when(userRepository.existsByEmail("race@test.com")).thenReturn(false);
                when(passwordEncoder.encode(anyString())).thenReturn("hashed");
                when(userRepository.save(any()))
                                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'race@test.com'"));

                assertThatThrownBy(() -> authService.signup(req))
                                .isInstanceOf(BusinessException.class)
                                .hasMessage("Email already registered")
                                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        }
}
