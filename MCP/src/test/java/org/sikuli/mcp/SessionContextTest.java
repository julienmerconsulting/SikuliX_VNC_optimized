/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.sikuli.mcp.server.SessionContext;

import static org.junit.jupiter.api.Assertions.*;

class SessionContextTest {

  @Test
  void newSessionMintsUniqueId() {
    SessionContext a = SessionContext.newSession(null, null);
    SessionContext b = SessionContext.newSession(null, null);
    assertNotEquals(a.sessionId, b.sessionId);
    assertNotNull(a.sessionId);
  }

  @Test
  void withLlmPreservesSessionId() {
    // Regression guard: historically withLlm() regenerated the UUID every
    // time, which shredded session continuity in the audit chain whenever
    // a client passed _meta.llm per request.
    SessionContext base = SessionContext.newSession(
        new JSONObject().put("name", "claude-desktop"), null);
    SessionContext updated = base.withLlm(
        new JSONObject().put("backend", "claude-opus"));

    assertEquals(base.sessionId, updated.sessionId,
        "withLlm must preserve the session id");
    assertSame(base.clientInfo, updated.clientInfo,
        "withLlm must preserve clientInfo");
    assertNotNull(updated.llmInfo);
    assertEquals("claude-opus", updated.llmInfo.getString("backend"));
  }

  @Test
  void withLlmCanBeChained() {
    SessionContext base = SessionContext.newSession(null, null);
    SessionContext s = base
        .withLlm(new JSONObject().put("backend", "a"))
        .withLlm(new JSONObject().put("backend", "b"))
        .withLlm(new JSONObject().put("backend", "c"));
    assertEquals(base.sessionId, s.sessionId);
    assertEquals("c", s.llmInfo.getString("backend"));
  }
}
