/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.transport.BearerAuth;

import static org.junit.jupiter.api.Assertions.*;

class BearerAuthTest {

  @Test
  void acceptsMatchingToken() {
    BearerAuth a = new BearerAuth("s3cr3t");
    assertTrue(a.accepts("Bearer s3cr3t"));
  }

  @Test
  void rejectsMissingHeader() {
    BearerAuth a = new BearerAuth("s3cr3t");
    assertFalse(a.accepts(null));
    assertFalse(a.accepts(""));
  }

  @Test
  void rejectsWrongScheme() {
    BearerAuth a = new BearerAuth("s3cr3t");
    assertFalse(a.accepts("Basic s3cr3t"));
    assertFalse(a.accepts("s3cr3t"));
  }

  @Test
  void rejectsWrongToken() {
    BearerAuth a = new BearerAuth("s3cr3t");
    assertFalse(a.accepts("Bearer nope"));
    assertFalse(a.accepts("Bearer s3cr3T")); // case-sensitive
  }

  @Test
  void rejectsEmptyToken() {
    BearerAuth a = new BearerAuth("s3cr3t");
    assertFalse(a.accepts("Bearer "));
    assertFalse(a.accepts("Bearer   "));
  }

  @Test
  void constructorRejectsEmptyToken() {
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth(""));
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth(null));
    assertThrows(IllegalArgumentException.class, () -> new BearerAuth("   "));
  }

  @Test
  void generatedTokensAreDistinctAndUrlSafe() {
    String a = BearerAuth.generateToken();
    String b = BearerAuth.generateToken();
    assertNotEquals(a, b);
    assertTrue(a.length() >= 40, "Token should carry ≥256 bits of entropy: " + a);
    assertTrue(a.matches("[A-Za-z0-9_-]+"), "Token must be URL-safe: " + a);
  }
}
