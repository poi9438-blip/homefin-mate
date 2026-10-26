package com.homefinmate.security;

import com.homefinmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 서버 시작 시 ADMIN_EMAILS에 있는 기존 사용자를 ADMIN으로 (로그에는 이메일 대신 사용자 id)
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminEmails adminEmails;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String email : adminEmails.all()) {
            userRepository.findByEmail(email)
                    .filter(user -> user.getRole() != Role.ADMIN)
                    .ifPresent(user -> {
                        user.setRole(Role.ADMIN);
                        log.info("Promoted to ADMIN from ADMIN_EMAILS: userId={}", user.getId());
                    });
        }
    }
}
