package com.codecafe.aicodingagent.dto;

import com.codecafe.aicodingagent.domain.CodingSession;

import java.time.Instant;
import java.util.List;

public record SessionDetailDto(
        String id, String title, String status, String repositoryPath,
        Instant createdAt, Instant updatedAt, Instant endedAt,
        List<MessageDto> messages, List<ToolCallDto> toolCalls) {

    public static SessionDetailDto from(CodingSession session, List<MessageDto> messages, List<ToolCallDto> toolCalls) {
        return new SessionDetailDto(
                session.getId(), session.getTitle(), session.getStatus().name(), session.getRepositoryPath(),
                session.getCreatedAt(), session.getUpdatedAt(), session.getEndedAt(), messages, toolCalls);
    }
}
