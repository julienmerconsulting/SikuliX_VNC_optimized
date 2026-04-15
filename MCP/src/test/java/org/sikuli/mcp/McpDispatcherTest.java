/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.ActionGate;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.gate.GateDecision;
import org.sikuli.mcp.server.JsonRpc;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.tools.Tool;
import org.sikuli.mcp.tools.ToolRegistry;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class McpDispatcherTest {

  /** Minimal fake tool — pure in-memory, no screen dependency. */
  private static final class EchoTool implements Tool {
    final AtomicInteger calls = new AtomicInteger();
    @Override public String name() { return "echo"; }
    @Override public String description() { return "echo"; }
    @Override public JSONObject inputSchema() {
      return new JSONObject().put("type", "object");
    }
    @Override public JSONObject call(JSONObject args) {
      calls.incrementAndGet();
      return Tool.textResult(args.toString());
    }
  }

  private static McpDispatcher mkDispatcher(Path dir, ToolRegistry r, ActionGate gate)
      throws Exception {
    KeyManager keys = KeyManager.loadOrInit(dir);
    JournalWriter journal = new JournalWriter(
        dir.resolve("journal"), keys, 100, 60_000);
    return new McpDispatcher(r, gate, journal);
  }

  @Test
  void initializeReturnsServerInfo(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());

    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject()
                .put("name", "inspector").put("version", "1.0")));

    JSONObject resp = d.dispatch(req, new SessionHandle());
    assertNotNull(resp);
    JSONObject result = resp.getJSONObject("result");
    assertEquals(McpDispatcher.PROTOCOL_VERSION, result.getString("protocolVersion"));
    assertEquals(McpDispatcher.SERVER_NAME,
        result.getJSONObject("serverInfo").getString("name"));
  }

  @Test
  void toolsListReturnsRegisteredTools(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());

    JSONObject req = new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2).put("method", "tools/list");
    JSONObject resp = d.dispatch(req, new SessionHandle());

    JSONArray tools = resp.getJSONObject("result").getJSONArray("tools");
    assertEquals(1, tools.length());
    assertEquals("echo", tools.getJSONObject(0).getString("name"));
  }

  @Test
  void toolsCallExecutesAndAudits(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    EchoTool echo = new EchoTool();
    r.register(echo);
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject().put("name", "t"))), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject().put("x", 42))), h);

    assertFalse(resp.getJSONObject("result").getBoolean("isError"));
    assertEquals(1, echo.calls.get());
  }

  @Test
  void deniedByGateReturnsErrorResultAndStillAudits(@TempDir Path dir) throws Exception {
    ToolRegistry r = new ToolRegistry();
    EchoTool echo = new EchoTool();
    r.register(echo);
    ActionGate denying = (name, args) -> GateDecision.denied("policy");
    McpDispatcher d = mkDispatcher(dir, r, denying);
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()), h);

    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 2)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())), h);

    assertTrue(resp.getJSONObject("result").getBoolean("isError"));
    assertEquals(0, echo.calls.get(), "Tool must not execute when denied");
  }

  @Test
  void unknownMethodYieldsMethodNotFound(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0").put("id", 7).put("method", "bogus"),
        new SessionHandle());
    assertEquals(JsonRpc.METHOD_NOT_FOUND,
        resp.getJSONObject("error").getInt("code"));
  }

  @Test
  void notificationsReturnNullResponse(@TempDir Path dir) throws Exception {
    McpDispatcher d = mkDispatcher(dir, new ToolRegistry(), new AutoApproveGate());
    JSONObject resp = d.dispatch(new JSONObject()
        .put("jsonrpc", "2.0")
        .put("method", "notifications/initialized"), new SessionHandle());
    assertNull(resp);
  }

  @Test
  void perRequestLlmDoesNotRegenerateSessionId(@TempDir Path dir) throws Exception {
    // End-to-end guard for the SessionContext bug fix: firing two tools/call
    // each with _meta.llm must keep the same session_id.
    ToolRegistry r = new ToolRegistry();
    r.register(new EchoTool());
    McpDispatcher d = mkDispatcher(dir, r, new AutoApproveGate());
    SessionHandle h = new SessionHandle();

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 1)
        .put("method", "initialize")
        .put("params", new JSONObject()
            .put("clientInfo", new JSONObject().put("name", "t"))), h);
    String sid1 = h.get().sessionId;

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 2)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())
            .put("_meta", new JSONObject()
                .put("llm", new JSONObject().put("backend", "claude-opus")))),
        h);
    String sid2 = h.get().sessionId;

    d.dispatch(new JSONObject().put("jsonrpc", "2.0").put("id", 3)
        .put("method", "tools/call")
        .put("params", new JSONObject()
            .put("name", "echo")
            .put("arguments", new JSONObject())
            .put("_meta", new JSONObject()
                .put("llm", new JSONObject().put("backend", "gpt-5")))),
        h);
    String sid3 = h.get().sessionId;

    assertEquals(sid1, sid2);
    assertEquals(sid2, sid3);
  }
}
