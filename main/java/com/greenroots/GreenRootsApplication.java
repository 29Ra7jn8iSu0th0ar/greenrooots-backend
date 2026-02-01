package com.greenroots;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableJpaAuditing
@EnableScheduling
public class GreenRootsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenRootsApplication.class, args);
    }
}
