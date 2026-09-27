package com.codecafe.aicodingagent.dto;

import com.codecafe.aicodingagent.domain.CodingSession;

import java.time.Instant;

public record SessionListItemDto(
        String id, String title, String status, String repositoryPath,
        Instant createdAt, Instant updatedAt) {

    public static SessionListItemDto from(CodingSession session) {
        return new SessionListItemDto(
                session.getId(), session.getTitle(), session.getStatus().name(),
                session.getRepositoryPath(), session.getCreatedAt(), session.getUpdatedAt());
    }
}
