package com.bidops.auth;

import com.bidops.domain.organization.entity.Organization;
import com.bidops.domain.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 시작 시 기본 조직과 관리자 계정이 없으면 생성.
 * MVP용. 실서비스에서는 마이그레이션 SQL로 대체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OrganizationRepository organizationRepository;

    private static final String SEED_ORG_NAME = "BidOps Default";
    private static final String SEED_EMAIL = "admin@bidops.io";
    private static final String SEED_PASSWORD = "bidops123";
    private static final String SEED_NAME = "관리자";

    @Override
    public void run(String... args) {
        Organization org = ensureDefaultOrganization();
        ensureSeedUser(org);
    }

    private Organization ensureDefaultOrganization() {
        return organizationRepository.findByName(SEED_ORG_NAME)
                .orElseGet(() -> {
                    Organization org = Organization.builder()
                            .name(SEED_ORG_NAME)
                            .build();
                    organizationRepository.save(org);
                    log.info("[Init] 기본 조직 생성: {}", SEED_ORG_NAME);
                    return org;
                });
    }

    private void ensureSeedUser(Organization org) {
        var existing = userRepository.findByEmail(SEED_EMAIL);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getOrganizationId() == null) {
                user.setOrganizationId(org.getId());
                userRepository.save(user);
                log.info("[Init] 기존 사용자에 조직 연결: {} → {}", SEED_EMAIL, org.getName());
            } else {
                log.info("[Init] 기본 사용자 이미 존재: {}", SEED_EMAIL);
            }
            return;
        }

        User user = User.builder()
                .email(SEED_EMAIL)
                .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                .name(SEED_NAME)
                .organizationId(org.getId())
                .build();
        userRepository.save(user);
        log.info("[Init] 기본 사용자 생성: {} / {}", SEED_EMAIL, SEED_PASSWORD);
    }
}
