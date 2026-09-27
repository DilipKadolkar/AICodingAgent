package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.domain.ToolName;
import com.codecafe.aicodingagent.exception.CommandNotApprovedException;
import com.codecafe.aicodingagent.service.ApprovedCommandRegistry;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ExecuteCommandTool implements CodeAgentTool {

    private final ApprovedCommandRegistry approvedCommandRegistry;
    private final AgentProperties properties;

    public ExecuteCommandTool(ApprovedCommandRegistry approvedCommandRegistry, AgentProperties properties) {
        this.approvedCommandRegistry = approvedCommandRegistry;
        this.properties = properties;
    }

    @Override
    public ToolName name() {
        return ToolName.EXECUTE_COMMAND;
    }

    @Override
    public ToolExecutionResult execute(ToolContext context, Map<String, Object> params) {
        Object commandObj = params.get("command");
        if (commandObj == null || String.valueOf(commandObj).isBlank()) {
            return ToolExecutionResult.failure("A non-empty 'command' parameter is required.");
        }
        String command = String.valueOf(commandObj).trim();

        try {
            approvedCommandRegistry.validate(command);
        } catch (CommandNotApprovedException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }

        List<String> tokens = List.of(command.split("\\s+"));
        ProcessBuilder builder = new ProcessBuilder(tokens)
                .directory(context.repositoryRoot().toFile())
                .redirectErrorStream(true);

        long start = System.currentTimeMillis();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return ToolExecutionResult.failure("Failed to start command '" + command + "': " + e.getMessage());
        }

        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (output.length() < properties.maxToolOutputLength()) {
                        output.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // process stream closed; nothing more to read
            }
        });
        reader.setDaemon(true);
        reader.start();

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, properties.commandTimeoutSeconds()), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return ToolExecutionResult.failure("Command execution was interrupted: " + command);
        }

        long durationMillis = System.currentTimeMillis() - start;

        if (!finished) {
            process.destroyForcibly();
            return ToolExecutionResult.failure("Command timed out after "
                    + properties.commandTimeoutSeconds() + "s and was terminated: " + command);
        }

        try {
            reader.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        int exitCode = process.exitValue();
        String truncatedOutput = output.length() > properties.maxToolOutputLength()
                ? output.substring(0, properties.maxToolOutputLength()) + "\n... (truncated)"
                : output.toString();

        String summary = "Ran `" + command + "` (exit " + exitCode + ", " + durationMillis + "ms)";
        return ToolExecutionResult.ok(summary, Map.of(
                "command", command,
                "exitCode", exitCode,
                "output", truncatedOutput,
                "durationMillis", durationMillis
        ));
    }
}
