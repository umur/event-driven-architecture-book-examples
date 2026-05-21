package com.umurinan.eda.ch07;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SagasApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagasApplication.class, args);
    }
}
