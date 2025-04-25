package com.leaderboard.demo.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    public static final String USER_SERVICE_BASE_URL = "http://localhost:8081";
    public static final String PROJECT_SERVICE_BASE_URL = "http://localhost:8082";
}
