package com.codecafe.aicodingagent.service;

public record OllamaHealthStatus(boolean reachable, boolean modelAvailable, String detail) {

    public static OllamaHealthStatus ok(String model) {
        return new OllamaHealthStatus(true, true, "Ollama is reachable and '" + model + "' is available.");
    }

    public static OllamaHealthStatus modelMissing(String model) {
        return new OllamaHealthStatus(true, false,
                "Ollama is reachable but model '" + model + "' is not pulled yet. Run: ollama pull " + model);
    }

    public static OllamaHealthStatus unreachable(String baseUrl) {
        return new OllamaHealthStatus(false, false,
                "Ollama is not reachable at " + baseUrl + ". Start Ollama and try again.");
    }
}
