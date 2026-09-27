package com.codecafe.aicodingagent.controller;

import com.codecafe.aicodingagent.service.OllamaClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OllamaClientService ollamaClientService;

    public HealthController(OllamaClientService ollamaClientService) {
        this.ollamaClientService = ollamaClientService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health/ollama")
    public Map<String, Object> ollamaHealth() {
        var status = ollamaClientService.checkHealth();
        return Map.of(
                "reachable", status.reachable(),
                "modelAvailable", status.modelAvailable(),
                "detail", status.detail()
        );
    }
}
