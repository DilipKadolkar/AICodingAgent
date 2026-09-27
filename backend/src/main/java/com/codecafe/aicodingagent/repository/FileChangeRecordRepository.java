package com.codecafe.aicodingagent.repository;

import com.codecafe.aicodingagent.domain.FileChangeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileChangeRecordRepository extends JpaRepository<FileChangeRecord, String> {

    List<FileChangeRecord> findBySession_IdOrderByCreatedAtAsc(String sessionId);
}
