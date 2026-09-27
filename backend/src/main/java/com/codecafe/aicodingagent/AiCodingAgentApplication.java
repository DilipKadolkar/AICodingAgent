package com.codecafe.aicodingagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCodingAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodingAgentApplication.class, args);
    }
}
