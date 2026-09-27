package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;

import java.util.Map;

/** Common strategy interface for every tool the agent can invoke. */
public interface CodeAgentTool {

    ToolName name();

    ToolExecutionResult execute(ToolContext context, Map<String, Object> params);
}
