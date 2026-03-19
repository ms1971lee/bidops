package com.bidops.common.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * StorageService Bean 등록.
 * bidops.storage.provider 값에 따라 local/s3 전환.
 *
 * 설정 예시:
 *   bidops.storage.provider=local  (기본값)
 *   bidops.storage.provider=s3
 */
@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "bidops.storage.provider", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService() {
        log.info("[StorageConfig] provider=local");
        return new LocalStorageService();
    }

    @Bean
    @ConditionalOnProperty(name = "bidops.storage.provider", havingValue = "s3")
    public StorageService s3StorageService() {
        log.info("[StorageConfig] provider=s3");
        return new S3StorageService();
    }
}
