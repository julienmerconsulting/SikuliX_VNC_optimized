/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.transport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HTTP bearer-token authentication for the Streamable HTTP transport.
 *
 * <p>Stdio has no auth because it runs as the same OS user as the client.
 * The moment the server listens on a socket, this is no longer true —
 * anyone who can reach the port can drive the mouse. A bearer token is
 * the minimum bar. Whether it's actually strong enough depends on how it
 * is distributed (env var on a trusted host: ok; shared Slack message: no).
 *
 * <p>The token is compared in constant time to avoid timing side channels.
 */
public final class BearerAuth {

  private static final String PREFIX = "Bearer ";

  private final byte[] expectedDigest;

  public BearerAuth(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Bearer token must be non-empty");
    }
    this.expectedDigest = sha256(token);
  }

  /**
   * Validate an {@code Authorization} header value. Returns {@code true}
   * iff the header is well-formed and matches the configured token.
   */
  public boolean accepts(String authorizationHeader) {
    if (authorizationHeader == null) return false;
    if (!authorizationHeader.startsWith(PREFIX)) return false;
    String presented = authorizationHeader.substring(PREFIX.length()).trim();
    if (presented.isEmpty()) return false;
    byte[] presentedDigest = sha256(presented);
    return MessageDigest.isEqual(expectedDigest, presentedDigest);
  }

  /**
   * Generate a URL-safe random token, ~256 bits of entropy. Used when the
   * operator launches {@code oculix-mcp serve} without pre-provisioning a
   * token — the server mints one and prints it to stderr so it can be
   * pasted into the MCP client config.
   */
  public static String generateToken() {
    byte[] buf = new byte[32];
    new SecureRandom().nextBytes(buf);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
  }

  private static byte[] sha256(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return md.digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
