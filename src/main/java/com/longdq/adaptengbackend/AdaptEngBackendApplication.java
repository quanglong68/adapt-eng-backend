package com.longdq.adaptengbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdaptEngBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdaptEngBackendApplication.class, args);
    }

}
