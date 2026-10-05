package com.homefinmate.repository;

import com.homefinmate.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// 회원 저장/조회 (구현 객체는 Spring Data JPA가 자동 생성)
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // 이메일로 회원 조회
    Optional<User> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);
}
