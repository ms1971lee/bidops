package com.bidops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BidOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(BidOpsApplication.class, args);
    }
}
