/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import org.json.JSONObject;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.mcp.gate.ActionGate;
import org.sikuli.mcp.gate.GateDecision;
import org.sikuli.mcp.tools.ScreenLock;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;

/**
 * Transport-agnostic dispatcher for MCP JSON-RPC messages.
 *
 * <p>Takes one inbound JSON-RPC request as a {@link JSONObject} and returns
 * the response {@link JSONObject} (or {@code null} for notifications).
 *
 * <p>All state shared across tool calls is held per-{@link SessionContext}.
 * A single dispatcher instance can serve multiple sessions concurrently
 * (e.g. in the HTTP transport where several clients share the same process).
 * Screen-mutating tool calls are serialized through a shared {@link ScreenLock}
 * so that two concurrent clicks cannot interleave.
 */
public final class McpDispatcher {

  public static final String PROTOCOL_VERSION = "2024-11-05";
  public static final String SERVER_NAME = "oculix-mcp-server";
  public static final String SERVER_VERSION = "3.0.1";

  private final ToolRegistry tools;
  private final ActionGate gate;
  private final JournalWriter journal;
  private final ScreenLock screenLock;

  public McpDispatcher(ToolRegistry tools, ActionGate gate, JournalWriter journal) {
    this(tools, gate, journal, new ScreenLock());
  }

  public McpDispatcher(ToolRegistry tools, ActionGate gate,
                       JournalWriter journal, ScreenLock screenLock) {
    this.tools = tools;
    this.gate = gate;
    this.journal = journal;
    this.screenLock = screenLock;
  }

  /**
   * Dispatch a single JSON-RPC message in the given session context.
   *
   * <p>Returns:
   * <ul>
   *   <li>a response {@link JSONObject} for request messages</li>
   *   <li>{@code null} for notifications (no response expected)</li>
   * </ul>
   *
   * <p>For {@code initialize} requests the dispatcher updates
   * {@link SessionContext} inside the {@link SessionHandle} in place, so
   * subsequent calls on the same handle see the captured {@code clientInfo}.
   */
  public JSONObject dispatch(JSONObject req, SessionHandle handle) {
    Object id = req.has("id") ? req.get("id") : null;
    String method = req.optString("method", null);
    JSONObject params = req.optJSONObject("params");
    if (params == null) params = new JSONObject();

    if (method == null) {
      return JsonRpc.error(id, JsonRpc.INVALID_REQUEST, "Missing 'method'");
    }

    switch (method) {
      case "initialize":
        return handleInitialize(id, params, handle);
      case "notifications/initialized":
      case "notifications/cancelled":
        return null;
      case "ping":
        return JsonRpc.result(id, new JSONObject());
      case "tools/list":
        return JsonRpc.result(id, new JSONObject().put("tools", tools.listAsJson()));
      case "tools/call":
        return handleToolsCall(id, params, handle);
      default:
        if (method.startsWith("notifications/")) return null;
        return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown method: " + method);
    }
  }

  private JSONObject handleInitialize(Object id, JSONObject params, SessionHandle handle) {
    JSONObject clientInfo = params.optJSONObject("clientInfo");
    handle.set(SessionContext.newSession(clientInfo, extractLlmInfo(params)));

    JSONObject result = new JSONObject()
        .put("protocolVersion", PROTOCOL_VERSION)
        .put("serverInfo", new JSONObject()
            .put("name", SERVER_NAME)
            .put("version", SERVER_VERSION))
        .put("capabilities", new JSONObject()
            .put("tools", new JSONObject()));
    return JsonRpc.result(id, result);
  }

  private JSONObject handleToolsCall(Object id, JSONObject params, SessionHandle handle) {
    String toolName = params.optString("name", null);
    JSONObject args = params.optJSONObject("arguments");
    if (args == null) args = new JSONObject();

    // Capture per-request LLM metadata if the client provides it — without
    // minting a new session id (the bug we used to have).
    JSONObject perRequestLlm = extractLlmInfo(params);
    if (perRequestLlm != null) {
      handle.set(handle.get().withLlm(perRequestLlm));
    }

    if (toolName == null) {
      return JsonRpc.error(id, JsonRpc.INVALID_PARAMS, "Missing tool name");
    }
    Tool tool = tools.get(toolName);
    if (tool == null) {
      return JsonRpc.error(id, JsonRpc.METHOD_NOT_FOUND, "Unknown tool: " + toolName);
    }

    GateDecision decision = gate.decide(toolName, args);
    if (!decision.isApproved()) {
      JSONObject denied = Tool.errorResult("Denied by ActionGate: " + decision.reason);
      auditSafely(handle.get(), toolName, args, denied);
      return JsonRpc.result(id, denied);
    }

    JSONObject result;
    screenLock.lock();
    try {
      result = tool.call(args);
    } catch (Exception e) {
      result = Tool.errorResult(e.getClass().getSimpleName() + ": " + e.getMessage());
    } finally {
      screenLock.unlock();
    }

    auditSafely(handle.get(), toolName, args, result);
    return JsonRpc.result(id, result);
  }

  private void auditSafely(SessionContext session, String toolName,
                           JSONObject args, JSONObject result) {
    try {
      String resultHash = Hashing.sha256Hex(result.toString());
      journal.appendToolCall(
          session.sessionId, session.clientInfo, session.llmInfo,
          toolName, args, resultHash);
    } catch (Exception e) {
      // Audit write failure is critical. In stdio mode we exit; in HTTP mode
      // the transport layer decides what to do (we re-throw a runtime error).
      throw new AuditFailure("Failed to write audit entry", e);
    }
  }

  private static JSONObject extractLlmInfo(JSONObject params) {
    JSONObject meta = params.optJSONObject("_meta");
    if (meta == null) return null;
    return meta.optJSONObject("llm");
  }

  /** Raised when the journal write fails — non-recoverable. */
  public static final class AuditFailure extends RuntimeException {
    public AuditFailure(String msg, Throwable cause) { super(msg, cause); }
  }
}
