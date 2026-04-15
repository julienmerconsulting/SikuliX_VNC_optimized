/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes access to the physical screen/keyboard/mouse.
 *
 * <p>Rationale: OculiX ultimately drives {@code java.awt.Robot}, which has
 * a single OS-wide cursor and keyboard focus. Running two
 * {@code oculix_click_image} in parallel would interleave mouse moves and
 * key presses in nonsense order.
 *
 * <p>In stdio mode only one request is in flight at a time by construction,
 * so the lock is uncontended and essentially free. In HTTP mode multiple
 * clients share one process — {@code McpDispatcher} acquires this lock
 * around every {@code Tool.call} so that tool invocations are serialized
 * globally, regardless of which session issued them.
 *
 * <p>The lock is fair so that a hyperactive session cannot starve others.
 */
public final class ScreenLock {

  private final ReentrantLock lock = new ReentrantLock(true);

  public void lock() {
    lock.lock();
  }

  public void unlock() {
    lock.unlock();
  }

  public boolean isHeldByCurrentThread() {
    return lock.isHeldByCurrentThread();
  }
}
