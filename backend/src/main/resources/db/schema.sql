-- AI Coding Agent - reference schema (MySQL 8.0)
--
-- This file documents the database structure explicitly and is kept in
-- sync with the JPA entities under com.codecafe.aicodingagent.domain. It
-- is NOT wired to run automatically (the application uses
-- spring.jpa.hibernate.ddl-auto=update for local development convenience);
-- treat this as the reviewable source of truth for the schema, and apply
-- it manually (or migrate to Flyway/Liquibase) for a production setup.

CREATE DATABASE IF NOT EXISTS ai_coding_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_coding_agent;

CREATE TABLE IF NOT EXISTS coding_session (
    id              CHAR(36)      NOT NULL PRIMARY KEY,
    title           VARCHAR(255)  NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    repository_path VARCHAR(1024) NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    ended_at        DATETIME(6)   NULL,
    summary_text    LONGTEXT      NULL,
    summary_json    LONGTEXT      NULL,
    KEY idx_session_status_updated (status, updated_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS session_message (
    id               CHAR(36)     NOT NULL PRIMARY KEY,
    session_id       CHAR(36)     NOT NULL,
    role             VARCHAR(10)  NOT NULL,
    content          LONGTEXT     NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    sequence_number  INT          NOT NULL,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES coding_session (id) ON DELETE CASCADE,
    KEY idx_message_session_seq (session_id, sequence_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tool_call (
    id             CHAR(36)      NOT NULL PRIMARY KEY,
    session_id     CHAR(36)      NOT NULL,
    message_id     CHAR(36)      NULL,
    tool_name      VARCHAR(40)   NOT NULL,
    input_params   LONGTEXT      NULL,
    output_result  LONGTEXT      NULL,
    status         VARCHAR(10)   NOT NULL,
    started_at     DATETIME(6)   NOT NULL,
    finished_at    DATETIME(6)   NULL,
    backup_path    VARCHAR(1024) NULL,
    error_message  LONGTEXT      NULL,
    CONSTRAINT fk_toolcall_session FOREIGN KEY (session_id) REFERENCES coding_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_toolcall_message FOREIGN KEY (message_id) REFERENCES session_message (id) ON DELETE SET NULL,
    KEY idx_toolcall_session_tool (session_id, tool_name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS file_change_record (
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    session_id        CHAR(36)      NOT NULL,
    tool_call_id      CHAR(36)      NULL,
    file_path         VARCHAR(1024) NOT NULL,
    change_type       VARCHAR(10)   NOT NULL,
    backup_file_path  VARCHAR(1024) NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_filechange_session FOREIGN KEY (session_id) REFERENCES coding_session (id) ON DELETE CASCADE,
    CONSTRAINT fk_filechange_toolcall FOREIGN KEY (tool_call_id) REFERENCES tool_call (id) ON DELETE SET NULL,
    KEY idx_filechange_session (session_id)
) ENGINE=InnoDB;
