package com.codecafe.aicodingagent.repository;

import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.domain.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingSessionRepository extends JpaRepository<CodingSession, String> {

    Page<CodingSession> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<CodingSession> findByStatusInOrderByUpdatedAtDesc(List<SessionStatus> statuses);
}
