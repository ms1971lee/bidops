package com.bidops.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 시작 시 기본 관리자 계정이 없으면 생성.
 * MVP용. 실서비스에서는 마이그레이션 SQL로 대체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String SEED_EMAIL = "admin@bidops.io";
    private static final String SEED_PASSWORD = "bidops123";
    private static final String SEED_NAME = "관리자";

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_EMAIL)) {
            log.info("[Init] 기본 사용자 이미 존재: {}", SEED_EMAIL);
            return;
        }

        User user = User.builder()
                .email(SEED_EMAIL)
                .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                .name(SEED_NAME)
                .build();
        userRepository.save(user);
        log.info("[Init] 기본 사용자 생성: {} / {}", SEED_EMAIL, SEED_PASSWORD);
    }
}
