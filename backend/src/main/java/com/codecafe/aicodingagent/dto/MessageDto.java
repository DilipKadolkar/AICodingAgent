package com.codecafe.aicodingagent.dto;

import com.codecafe.aicodingagent.domain.SessionMessage;

import java.time.Instant;

public record MessageDto(String id, String role, String content, Instant createdAt, int sequenceNumber) {

    public static MessageDto from(SessionMessage m) {
        return new MessageDto(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt(), m.getSequenceNumber());
    }
}
