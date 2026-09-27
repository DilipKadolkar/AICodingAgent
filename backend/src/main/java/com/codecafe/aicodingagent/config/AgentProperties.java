package com.codecafe.aicodingagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        String allowedRepositoryBaseDir,
        int maxToolIterations,
        int commandTimeoutSeconds,
        int maxMessageLength,
        int maxToolOutputLength,
        List<String> approvedCommands) {

    public AgentProperties {
        if (maxMessageLength <= 0) {
            maxMessageLength = 8000;
        }
        if (maxToolOutputLength <= 0) {
            maxToolOutputLength = 20000;
        }
        if (approvedCommands == null) {
            approvedCommands = List.of();
        }
    }
}
