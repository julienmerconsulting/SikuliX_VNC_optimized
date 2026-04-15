/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of active MCP sessions for the HTTP transport.
 *
 * <p>The MCP Streamable HTTP spec surfaces sessions through the
 * {@code Mcp-Session-Id} header: the server emits it in the response to
 * {@code initialize}, and the client echoes it on every subsequent request.
 *
 * <p>A {@code SessionStore} maps session ids to the mutable
 * {@link SessionHandle} that the dispatcher reads and writes. In stdio
 * mode a single handle is enough — this store is only needed when one
 * process serves concurrent clients.
 */
public final class SessionStore {

  private final Map<String, SessionHandle> byId = new ConcurrentHashMap<>();

  /**
   * Return the handle for {@code id}, creating an empty one if absent.
   * Called at the start of every HTTP request that carries a session header.
   */
  public SessionHandle getOrCreate(String id) {
    Objects.requireNonNull(id, "session id");
    return byId.computeIfAbsent(id, k -> new SessionHandle());
  }

  /**
   * Return the handle for {@code id} if it exists, or {@code null}.
   * Used to enforce "initialize must come first" — a client that sends
   * a session id we don't know has violated the protocol.
   */
  public SessionHandle get(String id) {
    return id == null ? null : byId.get(id);
  }

  /**
   * Register a freshly-initialised handle under {@code id}. Returns the
   * previously-registered handle (if any) or {@code null}.
   */
  public SessionHandle put(String id, SessionHandle handle) {
    return byId.put(id, handle);
  }

  /**
   * Remove a session. Called on {@code DELETE /mcp} or on transport close.
   */
  public SessionHandle remove(String id) {
    return id == null ? null : byId.remove(id);
  }

  public int size() {
    return byId.size();
  }
}
