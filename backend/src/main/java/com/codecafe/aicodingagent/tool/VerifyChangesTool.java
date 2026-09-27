package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.domain.ToolName;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Higher-level tool that auto-detects the right validation command for the
 * project type and runs it, producing a structured pass/fail verification
 * result that can be displayed and stored on the session.
 */
@Component
public class VerifyChangesTool implements CodeAgentTool {

    private final AnalyzeStructureTool analyzeStructureTool;
    private final ExecuteCommandTool executeCommandTool;

    public VerifyChangesTool(AnalyzeStructureTool analyzeStructureTool, ExecuteCommandTool executeCommandTool) {
        this.analyzeStructureTool = analyzeStructureTool;
        this.executeCommandTool = executeCommandTool;
    }

    @Override
    public ToolName name() {
        return ToolName.VERIFY_CHANGES;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        String command = params.get("command") == null ? null : String.valueOf(params.get("command"));

        if (command == null) {
            ToolExecutionResult structure = analyzeStructureTool.execute(context, Map.of());
            String projectType = structure.success() ? String.valueOf(structure.data().get("projectType")) : "unknown";
            command = defaultCommandFor(projectType);
            if (command == null) {
                return ToolExecutionResult.failure(
                        "Could not determine a default verification command for project type '" + projectType
                                + "'. Pass an explicit 'command' parameter.");
            }
        }

        ToolExecutionResult execResult = executeCommandTool.execute(context, Map.of("command", command));
        if (!execResult.success()) {
            return ToolExecutionResult.failure("Verification could not run: " + execResult.errorMessage());
        }

        int exitCode = (int) execResult.data().get("exitCode");
        boolean passed = exitCode == 0;
        String summary = "Verification " + (passed ? "PASSED" : "FAILED") + " (`" + command + "`, exit " + exitCode + ")";

        return ToolExecutionResult.ok(summary, Map.of(
                "command", command,
                "passed", passed,
                "exitCode", exitCode,
                "output", execResult.data().get("output"),
                "durationMillis", execResult.data().get("durationMillis")
        ));
    }

    private String defaultCommandFor(String projectType) {
        return switch (projectType) {
            case "maven" -> "mvn test";
            case "gradle" -> "mvn test"; // gradle wrapper not on the approved list by default; adjust config as needed
            case "npm" -> "npm test";
            default -> null;
        };
    }
}
