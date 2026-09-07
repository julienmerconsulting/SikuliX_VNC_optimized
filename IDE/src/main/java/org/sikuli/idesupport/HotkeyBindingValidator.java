/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.idesupport;

import java.awt.event.KeyEvent;

/**
 * Validates a global hotkey binding before it is offered to the OS.
 *
 * <p>Global hotkeys are registered system-wide, so a bad binding does not merely
 * misbehave inside the IDE — it takes a key away from every other application.
 * Issue #449 was exactly that: a stop hotkey that lost its modifiers and ended up
 * registered as a bare {@code C}, swallowing the letter system-wide until the IDE
 * was quit. This class exists so the Preferences dialog can refuse such a binding
 * up front rather than discovering it after the fact.
 *
 * <p>Pure and Swing-free on purpose, so it can be unit-tested headlessly.
 *
 * <p>Modifier values follow the legacy {@code InputEvent.*_MASK} convention used
 * throughout {@link org.sikuli.basics.PreferencesUser} and reported by
 * {@code KeyEvent.getModifiers()}. Only the presence of modifier bits matters
 * here, so a value expressed in the extended {@code *_DOWN_MASK} convention is
 * also accepted — a non-zero modifier is a modifier either way.
 */
public class HotkeyBindingValidator {

  /** Outcome of validating a single binding. */
  public enum Result {
    /** Binding is usable. */
    OK(null),
    /** Only a modifier key was pressed — there is no actual key to bind. */
    MODIFIER_ONLY("prefHotkeyErrModifierOnly"),
    /** A printable key with no modifier would be captured globally — the #449 failure. */
    NEEDS_MODIFIER("prefHotkeyErrNeedsModifier"),
    /** The two hotkeys would be bound to the same combination. */
    DUPLICATE("prefHotkeyErrDuplicate");

    private final String messageKey;

    Result(String messageKey) {
      this.messageKey = messageKey;
    }

    public boolean isOk() {
      return this == OK;
    }

    /**
     * @return the i18n key for the message to show the user, or {@code null} for {@link #OK}
     */
    public String getMessageKey() {
      return messageKey;
    }
  }

  private HotkeyBindingValidator() {
  }

  /**
   * Validate one binding in isolation.
   *
   * @param keyCode   a {@code KeyEvent.VK_*} value
   * @param modifiers modifier mask; {@code 0} for none
   */
  public static Result validate(int keyCode, int modifiers) {
    if (isModifierKey(keyCode)) {
      return Result.MODIFIER_ONLY;
    }
    if (modifiers == 0 && !isStandaloneKey(keyCode)) {
      return Result.NEEDS_MODIFIER;
    }
    return Result.OK;
  }

  /**
   * Validate a binding and additionally reject it if it collides with the other
   * hotkey. {@code HotkeyManager} already refuses a duplicate registration, but
   * only logs it — checking here lets the dialog tell the user why.
   */
  public static Result validate(int keyCode, int modifiers, int otherKeyCode, int otherModifiers) {
    Result result = validate(keyCode, modifiers);
    if (!result.isOk()) {
      return result;
    }
    if (keyCode == otherKeyCode && modifiers == otherModifiers) {
      return Result.DUPLICATE;
    }
    return Result.OK;
  }

  /** True if the key is itself a modifier, so it cannot be the bound key. */
  private static boolean isModifierKey(int keyCode) {
    switch (keyCode) {
      case KeyEvent.VK_SHIFT:
      case KeyEvent.VK_CONTROL:
      case KeyEvent.VK_ALT:
      case KeyEvent.VK_ALT_GRAPH:
      case KeyEvent.VK_META:
        return true;
      default:
        return false;
    }
  }

  /**
   * True if the key is safe to bind with no modifier at all.
   *
   * <p>Function keys are dedicated — grabbing F13 globally costs no other
   * application the ability to type. A printable character is the opposite:
   * binding a bare letter is what made the IDE eat every {@code C} in #449.
   */
  private static boolean isStandaloneKey(int keyCode) {
    return keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12
        || keyCode >= KeyEvent.VK_F13 && keyCode <= KeyEvent.VK_F24;
  }
}
