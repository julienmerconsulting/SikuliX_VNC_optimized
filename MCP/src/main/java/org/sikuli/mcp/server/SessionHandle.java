/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.server;

/**
 * Thin mutable holder around a {@link SessionContext}.
 *
 * <p>The context is replaced on {@code initialize} and updated on every
 * {@code tools/call} that carries fresh {@code _meta.llm}. Keeping a handle
 * rather than passing the context by value lets the dispatcher mutate
 * state transparently for both stdio (single session) and HTTP (many
 * concurrent sessions owned by a {@link SessionStore}).
 *
 * <p>Access is synchronized because an HTTP transport may expose the same
 * handle to overlapping request threads for the same session id.
 */
public final class SessionHandle {

  private SessionContext ctx;

  public SessionHandle() {
    this.ctx = SessionContext.empty();
  }

  public SessionHandle(SessionContext initial) {
    this.ctx = initial;
  }

  public synchronized SessionContext get() {
    return ctx;
  }

  public synchronized void set(SessionContext next) {
    this.ctx = next;
  }
}
