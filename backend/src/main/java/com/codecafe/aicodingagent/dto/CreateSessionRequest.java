package com.codecafe.aicodingagent.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank(message = "must not be blank") String repositoryPath,
        String title) {
}
