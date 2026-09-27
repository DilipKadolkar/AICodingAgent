package com.codecafe.aicodingagent.repository;

import com.codecafe.aicodingagent.domain.SessionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionMessageRepository extends JpaRepository<SessionMessage, String> {

    List<SessionMessage> findBySession_IdOrderBySequenceNumberAsc(String sessionId);

    long countBySession_Id(String sessionId);
}
