# OculiX MCP Server

**Auditable visual-control MCP server for regulated environments.**

`oculix-mcp-server` exposes OculiX's visual automation capabilities — template matching, OCR, mouse/keyboard control — as [Model Context Protocol](https://modelcontextprotocol.io/) tools. Any MCP-compatible client (Claude Desktop, Claude Code, Cursor, custom clients) can drive the machine where the server runs, regardless of the LLM backend (Claude, GPT, Mistral, Gemini, Llama).

What makes this server different from other browser-scoped automation MCP servers is that every tool call is recorded in a cryptographically signed, append-only audit trail. Every action is attributable to a specific client, session and LLM backend, and any tampering is detectable.

---

## ⚠ Read this before using

This server gives an LLM the ability to click anywhere on your screen, type any text, and trigger any keyboard shortcut. The attack surface is significantly larger than browser-scoped automation (Playwright, Puppeteer), which is confined to a single tab.

**Never run OculiX MCP on a production workstation.**

Recommended usage:

- An isolated VM dedicated to agent automation
- A test environment where nothing of value is at risk
- A sandboxed user account with limited filesystem / network permissions

The audit trail is there to help you mitigate and investigate, not to prevent misuse. Each tool call produces an entry containing:

- Timestamp (UTC, microsecond precision) plus a monotonic per-file sequence number
- Session id (UUID)
- MCP client name and version (from the MCP handshake)
- LLM backend and user id if the client provides them in `_meta.llm`
- Tool name and full arguments
- SHA-256 hash of the result payload
- `prev_hash` chaining to the previous entry
- `entry_hash` of the entry itself
- Ed25519 signature of `entry_hash`

Rotation markers are signed by the outgoing key before a new key takes over, so the chain remains provable across key rotations. Deleting the private key manually will trigger a fail-fast refusal on the next start — the server never falls back to unsigned mode.

---

## What is actually in the jar

`oculix-mcp-server.jar` is a fat jar (`java -jar` runnable), but here is what it does and does not bundle:

- **Apertix / OpenCV 4.10 natives**: bundled for Windows x86_64, Linux x86_64, macOS x64/aarch64. Template matching works out of the box.
- **Tesseract natives for Linux and macOS**: NOT bundled (see [oculix-org/oculix#110](https://github.com/oculix-org/Oculix/issues/110)). Install Tesseract via your system package manager. On Windows, `tess4j` ships the natives so no extra install is needed.
- **PaddleOCR**: runs as a separate Python microservice (`paddleocr-server`, Flask). The MCP server talks to it via HTTP on `127.0.0.1:5000` by default. If PaddleOCR is not reachable, the server falls back to Tesseract transparently.

---

## Quick start

### 1. Build

```bash
mvn -pl MCP -am -DskipTests -Pmcp-fatjar clean package
# produces MCP/target/oculix-mcp-server.jar
```

### 2. First run

```bash
java -jar MCP/target/oculix-mcp-server.jar run
```

On first start, the server generates an Ed25519 key pair in `~/.oculix-mcp/` (permissions `600` on the private key). Subsequent runs reuse that pair.

### 3. Wire it to Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) or `%APPDATA%\Claude\claude_desktop_config.json` (Windows):

```json
{
  "mcpServers": {
    "oculix": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/oculix-mcp-server.jar",
        "run"
      ]
    }
  }
}
```

Restart Claude Desktop. The nine OculiX tools should now appear in the model's tool list.

### 4. Verify the audit trail

```bash
java -jar oculix-mcp-server.jar verify
```

Walks every `audit-*.jsonl` file in `~/.oculix-mcp/journal/`, re-computes hashes, verifies signatures, and checks chain continuity. Outputs `OK` / `FAIL` per file.

---

## Tool reference

| Tool | Purpose |
|------|---------|
| `oculix_find_image` | Locate a reference image on screen, return coordinates (non-blocking) |
| `oculix_click_image` | Find a reference image and left-click it |
| `oculix_exists_image` | Non-blocking presence check |
| `oculix_wait_for_image` | Block until a reference image appears, up to a timeout |
| `oculix_screenshot` | Capture the full screen or a region, return as base64 PNG |
| `oculix_type_text` | Type literal text at the keyboard focus |
| `oculix_key_combo` | Press a keyboard combination (Ctrl+C, Cmd+Tab, F5, ...) |
| `oculix_find_text` | OCR: locate a text string on screen, return bounding box |
| `oculix_read_text_in_region` | OCR: extract the text from a region *(open mode only — see [Modes](#modes-open-vs-confidential))* |
| `oculix_screenshot_to_disk` | Capture to a local PNG, return path + hash only *(confidential mode only)* |
| `oculix_ocr_to_disk` | OCR to a local text file, return path + hash only *(confidential mode only)* |

Each tool's JSON schema is exposed via `tools/list` on the MCP connection.

---

## CLI subcommands

```
oculix-mcp run                Start the MCP server over stdio (default)
oculix-mcp serve [flags]      Start the MCP server over HTTP (Streamable HTTP)
                              --host HOST   (default 127.0.0.1)
                              --port PORT   (default 7337, 0 for auto)
                              env OCULIX_MCP_TOKEN    bearer auth (generated if absent)
                              env OCULIX_MCP_MODE     open | confidential
                              env OCULIX_MCP_VAULT    confidential-mode landing dir
oculix-mcp rotate-key         Rotate the Ed25519 audit signing key
oculix-mcp recover            Record an unsigned-gap and start a fresh chain
oculix-mcp verify             Verify all audit journals
oculix-mcp verify FILE        Verify a specific file
oculix-mcp --help             Show usage
```

### Key rotation

Key rotation is a deliberate operation, not a side-effect of deleting a file:

```bash
oculix-mcp rotate-key
```

The command:

1. Writes a `rotation_end` marker to the current journal, signed by the outgoing key. The marker includes the SHA-256 of the new public key so operators can anchor trust.
2. Archives the outgoing key pair under `~/.oculix-mcp/archive/`.
3. Generates and installs a new Ed25519 key pair.
4. Writes a `rotation_begin` marker to a fresh journal, chained to the closing marker of the previous file.

### Recovery (broken chain)

If the private key is genuinely lost (disk failure, operator error) and the chain cannot be continued, use:

```bash
oculix-mcp recover
```

This writes an `UNSIGNED_GAP` marker and starts a fresh chain under a new key. The discontinuity is explicit and visible to `verify`.

---

## Startup refusal states

The server refuses to start in any of the following situations, with a clear message pointing at the recovery command:

| Situation | Behaviour |
|-----------|-----------|
| No key pair, no journal history | Initialise normally |
| No private key but journal history exists | **REFUSE** — run `recover` if intentional |
| Inconsistent key state (only one of priv/pub present) | **REFUSE** — run `recover` |
| Key pair present but unreadable / corrupted | **REFUSE** — restore from backup or `recover` |
| Current key does not verify the last journal entry | **REFUSE** — use `rotate-key` properly, or `recover` |
| Key pair present and consistent with journal | Start normally |

The server never falls back to unsigned mode. Silent degradation is a security anti-pattern.

---

## Audit journal format

One JSON object per line, in `~/.oculix-mcp/journal/audit-YYYYMMDD-HHMMSS.jsonl`. Rotation happens after 10 000 entries or 24 hours, whichever comes first.

Example entry:

```json
{
  "type": "tool_call",
  "ts_utc": "2026-04-14T19:23:45.123456Z",
  "seq": 42,
  "session_id": "c4a1f8e0-3b20-4d6a-9b3f-1f6e4d6c8a11",
  "client": { "name": "claude-desktop", "version": "1.4.2" },
  "llm": { "backend": "claude-opus-4-6", "user_id": "alice@example.com" },
  "tool": "oculix_click_image",
  "args": { "reference_path": "/tmp/button.png", "similarity": 0.85 },
  "result_sha256": "3b1ab...",
  "extra": null,
  "prev_hash": "0000...",
  "entry_hash": "f8c2...",
  "signature": "a7e9..."
}
```

Special entry types:

- `rotation_end` / `rotation_begin` — pair of markers at key / file rotation
- `clock_regression` — recorded when the wall clock regresses versus the monotonic reference (NTP sync going backward, VM pause)
- `recovery_gap` — written by the `recover` subcommand as a separate file

---

## LLM-agnostic by design

MCP is a protocol. This server has no vendor-specific logic:

- No Anthropic SDK, no OpenAI SDK
- No LLM-specific prompt engineering
- No knowledge of which model is on the other end

The `llm.backend` and `llm.user_id` fields in the audit trail are populated only if the client chooses to pass them in `_meta.llm`. Clients that don't are free to leave them null — the audit chain works either way.

---

## HTTP transport (Streamable HTTP)

Stdio is not the only transport. A remote MCP client (qaopslab, an on-prem
Mistral orchestrator, the official MCP Inspector) can reach the server over
HTTP on the loopback interface (or through a reverse proxy on a VLAN).

Start the HTTP server:

```bash
OCULIX_MCP_TOKEN=$(openssl rand -base64 32) \
  java -jar oculix-mcp-server.jar serve --host 127.0.0.1 --port 7337
```

Endpoint: `POST / GET / DELETE http://<host>:<port>/mcp`, per the MCP
Streamable HTTP spec (2024-11-05).

Every request must carry:

- `Authorization: Bearer $OCULIX_MCP_TOKEN` — missing or wrong yields `401`.
- `Mcp-Session-Id: <id>` — echoed by the client after `initialize`.
  Unknown ids yield `404`; missing on a non-initialize request yields `400`.

If `OCULIX_MCP_TOKEN` is not set, the server generates a random one-shot
token and prints it on stderr. Persist the env var for repeatable restarts
— never treat the printed token as durable.

### Concurrency

Screen actions are serialized through a fair lock: no matter how many
clients share one process, two `oculix_click_image` cannot interleave.
This is independent of the transport.

### MCP Inspector

```bash
npx @modelcontextprotocol/inspector \
  --transport streamable-http \
  --url http://127.0.0.1:7337/mcp \
  --header "Authorization: Bearer $OCULIX_MCP_TOKEN"
```

Then exercise `tools/list`, `tools/call`, verify session headers round-trip,
and confirm the audit journal populates on every call.

---

## Modes: open vs confidential

By default, the server registers the 9 tools described above. Two of them
return screen content inline to the LLM:

- `oculix_screenshot` — PNG bytes (base64) back to the model.
- `oculix_read_text_in_region` — OCR text back to the model.

For regulated workflows where captured content must **not** reach a
third-party LLM, start the server in confidential mode:

```bash
OCULIX_MCP_MODE=confidential \
OCULIX_MCP_TOKEN=... \
  java -jar oculix-mcp-server.jar serve
```

In confidential mode the two content-bearing tools are **not registered**.
The client's `tools/list` will not even mention them — there is no filter
to bypass, the capability is physically absent. They are replaced by:

- `oculix_screenshot_to_disk` — captures a PNG into the local vault,
  returns `{path, sha256, width, height, bytes}`. Pixels stay local.
- `oculix_ocr_to_disk` — writes the OCR output to a text file in the
  vault, returns `{path, sha256, engine, line_count, char_count}`.
  Text stays local.

Vault location: `$OCULIX_MCP_VAULT` if set, else `~/.oculix-mcp/vault/`,
restricted to owner only (`0700` on POSIX).

### What you can claim, and what you cannot

The confidential mode delivers these properties end-to-end:

| Claim | Holds? |
|-------|--------|
| Screen pixels never leave the host via the MCP channel | ✅ |
| OCR results never leave the host via the MCP channel | ✅ |
| Local audit chain records SHA-256 of every captured artefact | ✅ |
| The LLM cannot enumerate content-bearing tools | ✅ (they are absent from `tools/list`) |
| User prompts typed in the upstream chatbot never reach the LLM | ❌ — that is an upstream concern, not MCP's |
| Tool arguments (e.g. `type_text`) never reach the LLM | ❌ — the LLM is the one writing them |

The mode is a property of the **tool surface**, not of the LLM. Combine
it with an on-prem model (e.g. Mistral, Llama) if the threat model forbids
any network egress, or keep the hosted LLM (Claude, GPT) if you only need
to guarantee that bank-internal screen content stays local.

---

## What is not in V1

- **Human-in-the-loop approval**: the `ActionGate` interface exists and is called for every tool call, but the default `AutoApproveGate` always approves. A queue-and-notify implementation can plug into the same interface for per-action operator approval.
- **Multi-monitor region selection**: the `screen` field exists in the region schema but defaults to 0. Full multi-screen UX is scoped for later.
- **Server-initiated SSE notifications**: the `GET /mcp` stream is kept open but no notifications are pushed yet. Inspector treats the stream as "ready" and continues; adding progress events is a later enhancement.

---

## License

MIT, same as OculiX.
