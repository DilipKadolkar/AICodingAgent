package com.codecafe.aicodingagent.domain;

public enum ToolName {
    LIST_FILES,
    SEARCH_CODE,
    READ_FILE,
    ANALYZE_STRUCTURE,
    IDENTIFY_RELEVANT_FILES,
    CREATE_FILE,
    MODIFY_FILE,
    REPLACE_CODE_SECTION,
    EXECUTE_COMMAND,
    VERIFY_CHANGES;

    private static final java.util.Set<ToolName> EXPLORATION_TOOLS = java.util.Set.of(
            LIST_FILES, SEARCH_CODE, READ_FILE, ANALYZE_STRUCTURE, IDENTIFY_RELEVANT_FILES);

    private static final java.util.Set<ToolName> MUTATING_TOOLS = java.util.Set.of(
            CREATE_FILE, MODIFY_FILE, REPLACE_CODE_SECTION);

    public boolean isExploration() {
        return EXPLORATION_TOOLS.contains(this);
    }

    public boolean isMutating() {
        return MUTATING_TOOLS.contains(this);
    }
}
