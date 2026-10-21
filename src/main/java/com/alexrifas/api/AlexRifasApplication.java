package com.alexrifas.api;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.alexrifas.api.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class AlexRifasApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlexRifasApplication.class, args);
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
