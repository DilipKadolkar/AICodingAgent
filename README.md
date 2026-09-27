# AI Coding Agent — Code Cafe Coding Challenge

An AI coding assistant that explores a target repository, reasons about
code changes using a **local** Ollama model, and safely creates/modifies
files with backups, runs approved commands to verify changes, and produces
a full session transcript and summary.

- **Backend:** Java 21 + Spring Boot 3.3 (`/backend`)
- **Frontend:** React 18 + Vite (`/frontend`)
- **Database:** MySQL (local instance)
- **Local AI model:** Ollama (local instance) — no external/cloud AI APIs
  are called anywhere in this codebase

See [`ai-coding-agent-prompts.txt`](./ai-coding-agent-prompts.txt) for the
full prompt pack this implementation was built from (22 prompts, Prompt 0
scaffolding through Prompt 21 documentation, each with its own
Validation & Testing section), plus a traceability matrix mapping every
requirement (R1–R40) from the challenge brief to the prompt(s) that
implement it.

## Prerequisites

- Java 21, Maven
- Node.js 18+ and npm
- MySQL running locally, with a database/user matching the config below
  (or override via env vars) — **optional**, see "Running without MySQL" below
- Ollama running locally on `http://localhost:11434`, with a model pulled,
  e.g. `ollama pull llama3.1`

## Configuration

Backend config lives in `backend/src/main/resources/application.yml`, all
overridable via environment variables:

| Env var | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/ai_coding_agent...` | MySQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `agent_user` | MySQL user |
| `SPRING_DATASOURCE_PASSWORD` | `agent_pass` | MySQL password |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server |
| `OLLAMA_MODEL` | `llama3.1` | Model to use |
| `AGENT_ALLOWED_BASE_DIR` | *(unset = no restriction)* | If set, sessions can only target repos under this directory |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | Frontend dev origin |

The command allow-list (`agent.approved-commands`) and other agent limits
(max tool iterations, command timeout, message/tool-output length caps)
are also in `application.yml`.

## Running locally

```bash
# 1. Create the database (once)
mysql -u root -e "CREATE DATABASE ai_coding_agent CHARACTER SET utf8mb4;
CREATE USER IF NOT EXISTS 'agent_user'@'localhost' IDENTIFIED BY 'agent_pass';
GRANT ALL PRIVILEGES ON ai_coding_agent.* TO 'agent_user'@'localhost';"

# 2. Start Ollama and pull a model (once)
ollama pull llama3.1

# 3. Backend
cd backend
mvn spring-boot:run          # http://localhost:8080

# 4. Frontend (separate terminal)
cd frontend
npm install
npm run dev                  # http://localhost:5173
```

Open http://localhost:5173, start a session against any local repository
path, and chat with the agent. If Ollama isn't running, the app detects
this and shows a friendly banner/error rather than hanging or crashing —
this was verified directly in this sandbox, which has no Ollama runtime
available (see **Environment note** below).

## Running without MySQL

You don't need MySQL installed to try this locally. On startup, the
backend does a quick TCP check against the configured MySQL host/port
(`localhost:3306` by default); if it's not reachable, it automatically
falls back to a local file-based **H2** database instead (in MySQL
compatibility mode, so nothing else about the app changes) and prints:

```
[ai-coding-agent] MySQL is not reachable at localhost:3306 - falling back
to a local H2 database at ./data/ai_coding_agent.mv.db (set
DB_AUTO_FALLBACK_H2=false to disable this).
```

The H2 file persists across restarts just like a real database (verified:
stop the app, restart it, your sessions are still there). If MySQL becomes
available later, just start it before the app and it will be preferred
automatically — no config changes needed either way.

Env vars for this behavior:

| Env var | Default | Purpose |
|---|---|---|
| `DB_AUTO_FALLBACK_H2` | `true` | Set to `false` to disable the fallback and fail fast on an unreachable MySQL instead (e.g. for a real deployment) |
| `H2_DATA_PATH` | `./data/ai_coding_agent` | Where the H2 file database is stored when falling back |

## Architecture

```
React UI  --(fetch/SSE)-->  Spring Boot REST API  --(chat)-->  Ollama (local LLM)
                                     |
                                     v
                          AgentOrchestrator loop
                            |         |        |
                     exploration   file-mod   command/verify
                        tools        tools        tools
                                     |
                                     v
                                  MySQL (sessions, messages,
                                  tool calls, file changes)
```

- `AgentOrchestrator` (backend/.../service) drives one request/response
  turn: sends the conversation + tool descriptions to Ollama, parses a
  ```json {"tool": ..., "params": ...}``` block if the model wants to call
  a tool, executes it, feeds the result back, and repeats up to a
  configurable iteration cap. A file-modifying tool cannot run until an
  exploration tool has been used in the session (see the system prompt in
  `OllamaClientService`).
- Every user message, agent message, tool call, and file change is
  persisted as it happens (not just at the end), so a crash loses minimal
  data and a session can always be resumed from MySQL.
- `SessionSummaryService` builds the end-of-session summary (narrative +
  files inspected/modified + commands run + verification results +
  processing stats) and degrades gracefully to a structured-only summary
  if Ollama is unavailable.

## Security & safety

- Every filesystem tool resolves paths through `PathSafety`, which
  canonicalizes and rejects anything (including via a symlink) that
  resolves outside the session's repository root.
- `ExecuteCommandTool` only runs commands matching an explicit allow-list
  prefix (`agent.approved-commands`) and rejects shell metacharacters
  (`&&`, `;`, `|`, backticks, redirects) even when the prefix matches;
  commands run via `ProcessBuilder` with an explicit argument list, never
  a shell interpreter.
- `ModifyFileTool`/`ReplaceCodeSectionTool` always back up the original
  file before writing, and a failed write never leaves the original file
  half-modified.
- `agent.allowed-repository-base-dir`, if set, restricts which
  directories a session can be started against.
- No external/cloud AI SDK or API key exists anywhere in this codebase —
  the only network call for AI reasoning is to the configured local
  Ollama `base-url`.

## Tests

```bash
cd backend && mvn test     # 67 tests: tools, orchestrator, REST API, summary, DB fallback
cd frontend && npm test    # 26 tests: components, pages, validation, banners
```

## Environment note (this sandbox)

This application was built and started in an isolated cloud container
whose network policy blocks `ollama.com` and unattached GitHub release
downloads, so the actual Ollama runtime could not be installed here to
demo live AI responses. MySQL was installed and is fully wired up and
exercised end-to-end. The chat flow was verified live against this
constraint: creating a session, exploring tools, empty-message rejection,
and the Ollama-unreachable path (friendly error + persisted SYSTEM message
+ retry, session left in a consistent, resumable state) all work exactly
as designed. Point `OLLAMA_BASE_URL`/`OLLAMA_MODEL` at a real local Ollama
install to get live AI responses and tool-driven code changes.
