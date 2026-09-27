package com.codecafe.aicodingagent.tool;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.domain.CodingSession;
import com.codecafe.aicodingagent.service.ApprovedCommandRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecuteCommandToolTest {

    @TempDir
    Path repoRoot;

    private ToolContext context() {
        return new ToolContext(new CodingSession("t", repoRoot.toString()), repoRoot);
    }

    private ExecuteCommandTool toolWith(List<String> approved, int timeoutSeconds) {
        AgentProperties props = new AgentProperties("", 8, timeoutSeconds, 8000, 20000, approved);
        return new ExecuteCommandTool(new ApprovedCommandRegistry(props), props);
    }

    @Test
    void rejectsCommandNotOnAllowList() {
        ExecuteCommandTool tool = toolWith(List.of("echo approved"), 10);
        ToolExecutionResult result = tool.execute(context(), Map.of("command", "rm -rf /"));
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("not on the approved list");
    }

    @Test
    void runsAnApprovedCommandAndCapturesOutput() {
        ExecuteCommandTool tool = toolWith(List.of("echo hello-world"), 10);
        ToolExecutionResult result = tool.execute(context(), Map.of("command", "echo hello-world"));
        assertThat(result.success()).isTrue();
        assertThat(result.data().get("exitCode")).isEqualTo(0);
        assertThat(String.valueOf(result.data().get("output"))).contains("hello-world");
    }

    @Test
    void enforcesTimeoutOnALongRunningCommand() {
        ExecuteCommandTool tool = toolWith(List.of("sleep 5"), 1);
        long start = System.currentTimeMillis();
        ToolExecutionResult result = tool.execute(context(), Map.of("command", "sleep 5"));
        long elapsed = System.currentTimeMillis() - start;
        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("timed out");
        assertThat(elapsed).isLessThan(4000);
    }

    @Test
    void reportsNonZeroExitCodeAsSuccessfulToolCallWithFailingResult() {
        // The tool ran successfully (we got a result); the command itself failed.
        ExecuteCommandTool tool = toolWith(List.of("ls /no/such/path/at/all"), 10);
        ToolExecutionResult result = tool.execute(context(), Map.of("command", "ls /no/such/path/at/all"));
        assertThat(result.success()).isTrue();
        assertThat((int) result.data().get("exitCode")).isNotZero();
    }
}
