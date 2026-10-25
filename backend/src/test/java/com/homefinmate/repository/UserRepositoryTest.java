package com.homefinmate.repository;

import com.homefinmate.support.PersonalDataJpaConfig;
import org.springframework.context.annotation.Import;
import com.homefinmate.entity.User;
import com.homefinmate.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// UserRepository 쿼리 메서드와 email unique 제약 검증
@DataJpaTest
@Import(PersonalDataJpaConfig.class)
@ActiveProfiles("test")
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    private User newUser(String email) {
        return User.builder()
                .email(email)
                .password("hashed-password")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("[TC-DATA-001] 저장하면 id·가입 시각이 붙고 이메일로 조회됨")
    void save_and_findByEmail() {
        User saved = userRepository.save(newUser("demo@test.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(userRepository.findByEmail("demo@test.com"))
                .isPresent()
                .get()
                .extracting(User::getRole)
                .isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("[TC-DATA-002] 없는 이메일이면 빈 Optional, existsByEmail false")
    void notFound() {
        assertThat(userRepository.findByEmail("nobody@test.com")).isEmpty();
        assertThat(userRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    @DisplayName("[TC-DATA-003] 가입된 이메일이면 existsByEmail true")
    void existsByEmail() {
        userRepository.save(newUser("demo@test.com"));

        assertThat(userRepository.existsByEmail("demo@test.com")).isTrue();
    }

    @Test
    @DisplayName("[TC-DATA-004] 같은 이메일 두 번 저장은 DB unique 제약 위반")
    void duplicateEmail_violatesUniqueConstraint() {
        userRepository.save(newUser("demo@test.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(newUser("demo@test.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
