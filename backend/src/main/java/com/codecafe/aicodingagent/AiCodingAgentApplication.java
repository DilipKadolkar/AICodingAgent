package com.codecafe.aicodingagent;

import com.codecafe.aicodingagent.config.DatabaseFallbackConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCodingAgentApplication {

    public static void main(String[] args) {
        DatabaseFallbackConfigurer.configureIfNeeded();
        SpringApplication.run(AiCodingAgentApplication.class, args);
    }
}
