package com.codecafe.aicodingagent.dto;

import jakarta.validation.constraints.NotBlank;

public record PostMessageRequest(@NotBlank(message = "must not be empty") String content) {
}
