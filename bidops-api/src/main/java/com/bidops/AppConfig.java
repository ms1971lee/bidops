package com.bidops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AppConfig {

    /** Security Context에서 현재 사용자 ID 추출 → BaseEntity.createdBy / updatedBy */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated()) return Optional.of("system");
                return Optional.of(auth.getName());
            } catch (Exception e) {
                return Optional.of("system");
            }
        };
    }

    /** Service 계층에서 주입받아 사용하는 공유 ObjectMapper */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
