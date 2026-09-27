package com.codecafe.aicodingagent.repository;

import com.codecafe.aicodingagent.domain.ToolCall;
import com.codecafe.aicodingagent.domain.ToolName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ToolCallRepository extends JpaRepository<ToolCall, String> {

    List<ToolCall> findBySession_IdOrderByStartedAtAsc(String sessionId);

    List<ToolCall> findBySession_IdAndToolNameOrderByStartedAtAsc(String sessionId, ToolName toolName);

    long countBySession_IdAndToolName(String sessionId, ToolName toolName);

    boolean existsBySession_IdAndToolNameIn(String sessionId, Collection<ToolName> toolNames);
}
