/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide;

/**
 * Marker interface for components that need to participate in the theme-change
 * lifecycle. SikulixIDE dispatches a pair of events around every Dark/Light
 * toggle so implementers can tear down LaF-sensitive state before the global
 * {@code FlatLaf.updateUI()} runs, and rebuild it afterwards under the new
 * look and feel.
 *
 * <p>The dispatch order is:
 * <ol>
 *   <li>{@link #beforeThemeChange()} on every registered instance</li>
 *   <li>{@code FlatLaf.updateUI()} on the root window (done by the toggle
 *       button in OculixSidebar)</li>
 *   <li>{@link #afterThemeChange()} on every registered instance</li>
 * </ol>
 *
 * <p>Implementations should keep both methods idempotent and fast. Heavy work
 * (image decode, OCR) must be scheduled outside the EDT.
 */
public interface ThemeAware {
  /**
   * Save any state that will not survive {@code FlatLaf.updateUI()}
   * (embedded Swing components, icon caches, custom colors) and release the
   * references so the LaF swap can proceed safely.
   */
  void beforeThemeChange();

  /**
   * Rebuild the state saved in {@link #beforeThemeChange()} under the newly
   * active LaF. Also a good hook to re-apply custom colors / icons that the
   * LaF swap has reset to its defaults.
   */
  void afterThemeChange();
}
