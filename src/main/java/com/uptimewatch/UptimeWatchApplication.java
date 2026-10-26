package com.uptimewatch;

import com.uptimewatch.config.UptimeWatchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(UptimeWatchProperties.class)
public class UptimeWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(UptimeWatchApplication.class, args);
    }
}
