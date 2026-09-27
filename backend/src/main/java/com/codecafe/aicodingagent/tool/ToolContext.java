package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.CodingSession;

import java.nio.file.Path;

/** Everything a tool needs to operate safely within one session's repository. */
public record ToolContext(CodingSession session, Path repositoryRoot) {
}
