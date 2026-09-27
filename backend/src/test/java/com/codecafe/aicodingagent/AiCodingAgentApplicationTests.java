package com.codecafe.aicodingagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiCodingAgentApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (all tools, services, controllers) wires up cleanly.
    }
}
