/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.server.SessionHandle;
import org.sikuli.mcp.server.SessionStore;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

  @Test
  void getOrCreateIsIdempotent() {
    SessionStore s = new SessionStore();
    SessionHandle h1 = s.getOrCreate("abc");
    SessionHandle h2 = s.getOrCreate("abc");
    assertSame(h1, h2);
    assertEquals(1, s.size());
  }

  @Test
  void getReturnsNullForUnknown() {
    SessionStore s = new SessionStore();
    assertNull(s.get("nope"));
    assertNull(s.get(null));
  }

  @Test
  void removeReturnsPreviousHandle() {
    SessionStore s = new SessionStore();
    SessionHandle h = s.getOrCreate("x");
    assertSame(h, s.remove("x"));
    assertEquals(0, s.size());
    assertNull(s.remove("x"));
  }

  @Test
  void distinctIdsHaveDistinctHandles() {
    SessionStore s = new SessionStore();
    SessionHandle a = s.getOrCreate("a");
    SessionHandle b = s.getOrCreate("b");
    assertNotSame(a, b);
    assertEquals(2, s.size());
  }
}
