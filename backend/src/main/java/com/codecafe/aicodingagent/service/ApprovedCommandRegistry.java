package com.codecafe.aicodingagent.service;

import com.codecafe.aicodingagent.config.AgentProperties;
import com.codecafe.aicodingagent.exception.CommandNotApprovedException;
import org.springframework.stereotype.Service;

/**
 * A configurable allow-list of command prefixes the agent may execute.
 * Anything not matching is rejected with a clear reason - never silently
 * altered or force-run.
 */
@Service
public class ApprovedCommandRegistry {

    private static final String[] SHELL_METACHARACTERS = {
            "&&", "||", ";", "|", "`", "$(", ">", "<", "\n", "\r"
    };

    private final AgentProperties properties;

    public ApprovedCommandRegistry(AgentProperties properties) {
        this.properties = properties;
    }

    public void validate(String command) {
        if (command == null || command.isBlank()) {
            throw new CommandNotApprovedException("(empty command)");
        }
        String trimmed = command.trim();

        for (String meta : SHELL_METACHARACTERS) {
            if (trimmed.contains(meta)) {
                throw new CommandNotApprovedException(command
                        + " (contains disallowed shell metacharacter '" + meta + "')");
            }
        }

        boolean approved = properties.approvedCommands().stream().anyMatch(prefix -> matchesPrefix(trimmed, prefix));
        if (!approved) {
            throw new CommandNotApprovedException(command);
        }
    }

    public boolean isApproved(String command) {
        try {
            validate(command);
            return true;
        } catch (CommandNotApprovedException e) {
            return false;
        }
    }

    private boolean matchesPrefix(String command, String prefix) {
        if (!command.startsWith(prefix)) {
            return false;
        }
        return command.length() == prefix.length() || command.charAt(prefix.length()) == ' ';
    }
}
