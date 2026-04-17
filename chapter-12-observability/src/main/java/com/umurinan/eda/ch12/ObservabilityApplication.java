package com.umurinan.eda.ch12;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.umurinan.eda.ch12.config.KafkaConfig;

@SpringBootApplication
@Import(KafkaConfig.class)
public class ObservabilityApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservabilityApplication.class, args);
    }
}
