package com.umurinan.eda.ch03.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic watchlistUpdatedTopic() {
        return new NewTopic("watchlist-updated", 8, (short) 1);
    }

    @Bean
    public NewTopic watchlistUpdatedDltTopic() {
        return new NewTopic("watchlist-updated.DLT", 8, (short) 1);
    }
}
