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
- **Tesseract 5.5, Leptonica and five language models**: bundled for every platform via [Legerix](https://github.com/oculix-org/Legerix). Nothing to install: no `apt install tesseract-ocr`, no `brew install tesseract`. This closed [#110](https://github.com/oculix-org/Oculix/issues/110).
- **PaddleOCR**: runs as a separate Python microservice (`paddleocr-server`, Flask). The MCP server talks to it via HTTP on `127.0.0.1:5000` by default. If PaddleOCR is not reachable, the server falls back to Tesseract transparently.

---

## Quick start

**Prerequisite:** Java 17 or later — [Eclipse Temurin](https://adoptium.net) or [Azul Zulu](https://www.azul.com/downloads/). Every OculiX module compiles with `release 17`.

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

Runs a full chain sweep by default: every `audit-*.jsonl` in `~/.oculix-mcp/journal/` is checked per-file (entry hashes recomputed, Ed25519 signatures verified, `seq` and `prev_hash` chained), cross-file rotation links are resolved by hash lookup (not filename order), file heads must start at genesis or at a resolved `rotation_begin`, and the signed high-water-mark anchor at `~/.oculix-mcp/journal.hwm` is confronted to catch tail truncation.

Exit codes are deterministic: `0` clean, `2` soft warning (anchor lags queue after a write-then-crash), `1` hard fail (tamper, truncation, orphan file, unresolved cross-file link). Precedence is `1 > 2 > 0` — the ordering of files in the report never changes the exit code.

Use `verify --per-file` for the legacy single-file mode (or pass explicit paths). Handy when debugging one file in isolation, or in Phase 1 for files signed by an archived key: `verify` under the current key only.

See [Verify: chain mode and coverage](#verify-chain-mode-and-coverage) below for the exact list of attacks that are detected and the ones that are not.

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
oculix-mcp run                    Start over stdio (default — for Claude Desktop etc.)
oculix-mcp serve [flags]          Start over HTTP (Streamable HTTP)
                                  --host HOST   (default 127.0.0.1)
                                  --port PORT   (default 7337, 0 for auto)
                                  env OCULIX_MCP_TOKEN   optional client token
                                  env OCULIX_MCP_MODE    open | confidential
                                  env OCULIX_MCP_VAULT   confidential landing dir
                                  env OCULIX_MCP_TRUST_TLS_TERMINATION=1
                                                         acknowledge upstream TLS
oculix-mcp rotate-key             Rotate the Ed25519 audit signing key
oculix-mcp rotate-session-key     Rotate the HMAC keyring for session tokens
oculix-mcp recover                Record an unsigned-gap and start a fresh chain
oculix-mcp verify [--per-file] [FILES...]
                                  Default: full chain verification with anchor
                                  --per-file: legacy single-file mode
                                  Named FILES imply per-file mode
                                  Exit codes: 0 clean, 2 warn (soft), 1 fail
oculix-mcp --help                 Show usage
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

The command wipes the key files, generates a fresh Ed25519 pair, and writes a signed `RECOVERY_GAP` entry as the first line of a new `audit-*.jsonl` under the new key. The entry's `prev_hash` is forced back to genesis so the chain verifier identifies it as a legitimate fresh start. The high-water-mark anchor is re-signed under the new key at the same time.

A human-readable `UNSIGNED_GAP-*.txt` artefact is left in the journal directory for operators skimming with a file browser, but the verifier ignores it entirely — the anchor of trust is the signed `RECOVERY_GAP` entry, not a plain text file. This matters: if the verifier honoured `.txt` markers, an attacker with write access to the journal directory could delete an `audit-*.jsonl` and drop a fake `.txt` to explain the gap. Trusting only the signed entry keeps the level of proof at "control of the private key".

### Verify: chain mode and coverage

The default `verify` runs a full sweep across the journal directory plus a confrontation with a signed high-water-mark anchor. Four passes plus an anchor check:

1. **Per-file** — every `audit-*.jsonl` is checked line by line: entry hash recomputed, Ed25519 signature verified, `seq` monotonic, `prev_hash` chained, `ts_utc` monotonic.
2. **Index** — every `rotation_end` entry's `entry_hash` is indexed into a map. This is the source of truth for chain order, not filename order: `openNewFile` suffixes millisecond collisions with `-1`, `-2`, and lexicographic order places `audit-...-000-1.jsonl` before `audit-...-000.jsonl` even though the suffixed file was created later. The hash chain does not lie.
3. **Resolve** — every `rotation_begin` entry's `previous_marker_hash` is resolved against the index. A missing predecessor surfaces here as an unresolvable reference.
4. **Genesis-anchored heads** — any file whose first entry has `prev_hash != genesis` and whose type is not `rotation_begin` is orphaned (attacker-injected, or predecessor missing). Legitimate starts are the very first file, a post-`recover` file (whose first entry is a `RECOVERY_GAP` at genesis), and a rotation successor whose predecessor was resolved above.

The high-water-mark anchor lives at `~/.oculix-mcp/journal.hwm`, outside the journal directory so a cosmetic wipe of `journal/` leaves it behind. It is signed by the current key and holds `{last_entry_hash, last_seq, last_file, last_ts_utc}` — all four fields under the signature, including `last_file` (a rename must invalidate the anchor, otherwise a verifier resolving `last_file` → path could be misled without any signature failure to flag it). Every write to the journal updates the anchor; every key rotation or recovery re-signs it under the new key.

Coverage:

| Attack | Detected by |
|---|---|
| Modify an entry inside a file | Per-file entry hash + Ed25519 signature |
| Delete an entry mid-file | Per-file `prev_hash` chain |
| Delete a file in the middle of the directory | Cross-file `rotation_end`/`rotation_begin` hash resolution |
| Rewrite a `rotation_begin.previous_marker_hash` | Same, plus per-file entry hash |
| Inject an orphan `audit-*.jsonl` | Genesis-anchored head check |
| Delete the last `audit-*.jsonl` file(s) | Signed anchor references a file that no longer exists |
| Wipe of `journal/` alone | Anchor lives in `oculixDir`, references a missing file |
| Rename `last_file` in the anchor | `last_file` sits under the anchor's signature |
| Drop a fake `UNSIGNED_GAP-*.txt` to explain a deletion | Verifier ignores `.txt` entirely; only signed `RECOVERY_GAP` entries count |
| Wipe of the whole `oculixDir` (key + journal + anchor) | Not detected in Phase 1 — Phase 2 issue tracks an external anchor (other volume, syslog forward, notary) |
| Attacker with the private key | Not detectable by design — this is the security boundary the anchor and chain both rely on |

Exit codes:

| Code | Level | Meaning |
|---|---|---|
| `0` | OK | Chain intact, anchor agrees |
| `2` | WARN | Soft deviation: anchor lags actual queue (write reached disk, anchor update crashed) — chain itself intact |
| `1` | FAIL | Tamper, truncation, orphan, or unresolved cross-file link |

Precedence is `1 > 2 > 0` — the ordering of files in the report never changes the exit code.

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
HTTP on the loopback interface (or through a reverse proxy with TLS).

### Dev quick start — no auth to fiddle with

On loopback, with no pre-shared credential, you can get a full session in
three `curl` commands:

```bash
java -jar MCP/target/oculix-mcp-server.jar serve
#   → listening on http://127.0.0.1:7337/mcp
#   → client token: DISABLED (any caller on loopback can initialize)

# 1. initialize — returns Mcp-Session-Id (header) + bearer (body)
RESP=$(curl -s -D /tmp/h.txt -X POST http://127.0.0.1:7337/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}')
SID=$(grep -i mcp-session-id /tmp/h.txt | awk '{print $2}' | tr -d '\r')
BEARER=$(echo "$RESP" | jq -r '.result._meta.bearer')

# 2. tools/list
curl -s -X POST http://127.0.0.1:7337/mcp \
  -H "Mcp-Session-Id: $SID" \
  -H "Authorization: Bearer $BEARER" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | jq

# 3. DELETE — revoke the session
curl -s -X DELETE http://127.0.0.1:7337/mcp \
  -H "Mcp-Session-Id: $SID" \
  -H "Authorization: Bearer $BEARER" -o /dev/null -w "%{http_code}\n"
```

The MCP Inspector works the same way:

```bash
npx @modelcontextprotocol/inspector \
  --transport streamable-http \
  --url http://127.0.0.1:7337/mcp
```

No `--header` flag needed in dev mode — Inspector will issue a fresh
bearer on `initialize` and reuse it for the whole session.

### Auth model — two layers

**Layer 1: client credential (optional, PAT-style).** Gates `initialize`.
Configured via `OCULIX_MCP_TOKEN`. Unset → anyone on the bound interface can
initialize, which is acceptable on loopback. Set it when exposing the server
beyond localhost.

**Layer 2: session token (mandatory, ephemeral).** Minted by the server on
every successful `initialize`, returned in `result._meta.bearer`. Format:

```
ocx.<kid>.<b64url(payload)>.<b64url(hmac)>
```

Where the payload is canonical JSON:

```json
{"aud":"oculix-mcp","sid":"<uuid>","nonce":"<b64url 256 bits>","iat":<unix>,"exp":<unix>}
```

The client sends it back as `Authorization: Bearer <value>` on every
subsequent request. Default TTL is 30 minutes; refresh by re-initializing.

Verification chain on every request:

1. Structural parse + `ocx` prefix.
2. `kid` looked up in the server's keyring.
3. HMAC-SHA256 recomputed and compared in constant time
   (`MessageDigest.isEqual`).
4. Payload `aud` is `"oculix-mcp"`, `exp` not in the past (30 s skew).
5. `Mcp-Session-Id` header matches the token's embedded `sid`.
6. Server-side `SessionStore` still has the session and its nonce matches
   the token's nonce (constant time).

A leaked token **cannot** be replayed against a different session id; a
token whose session was `DELETE`d is rejected with `404` even if still
crypto-valid; key rotation leaves outstanding tokens valid until they
expire naturally.

### Session-token key rotation

```bash
oculix-mcp rotate-session-key
#   Session-token keyring rotated.
#     previous kid:  k9QwM7eL
#     new kid:       kX3pT2aB
#     ring size:     2
```

The HMAC keyring lives in `~/.oculix-mcp/session-hmac-keyring.json`
(permissions `0600`). Generating a new kid keeps old kids in the ring so
already-issued tokens remain verifiable until they expire. Delete the
file to force all outstanding sessions into hard failure.

### TLS

Plain HTTP on anything other than a loopback address is refused at startup.
If TLS is terminated upstream (nginx, Caddy, service mesh, WAF), set
`OCULIX_MCP_TRUST_TLS_TERMINATION=1` to acknowledge that responsibility
before binding `--host 0.0.0.0` or a non-local interface. In-process TLS
via `addHttpsListener` is planned but not shipped — use a reverse proxy
for now; certificate rotation and WAF policy belong there anyway.

### Concurrency

Screen actions are serialized through a fair lock: no matter how many
clients share one process, two `oculix_click_image` cannot interleave.
This is independent of the transport.

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
