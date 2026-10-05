package com.homefinmate;

import com.homefinmate.dto.auth.SignupRequest;
import com.homefinmate.dto.auth.SignupResponse;
import com.homefinmate.entity.User;
import com.homefinmate.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// AuthService 단위 테스트 (의존 객체는 Mockito 가짜로 대체)
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EncryptionUtil  encryptionUtil;
    @InjectMocks AuthService authService;

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

}
