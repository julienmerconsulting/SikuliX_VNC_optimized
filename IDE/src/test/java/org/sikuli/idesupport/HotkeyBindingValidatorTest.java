/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.idesupport;

import org.junit.jupiter.api.Test;
import org.sikuli.idesupport.HotkeyBindingValidator.Result;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the global hotkey binding rules introduced with #335.
 *
 * <p>The NEEDS_MODIFIER cases are the important ones: they are what stops a user
 * from reproducing #449 by hand through the Preferences dialog.
 */
class HotkeyBindingValidatorTest {

  private static final int SHIFT_META = KeyEvent.SHIFT_MASK + KeyEvent.META_MASK; // legacy, 5

  // ── rejected: bare printable keys (the #449 shape) ───────────────────────

  @Test
  void bareLetterIsRejected() {
    assertEquals(Result.NEEDS_MODIFIER,
        HotkeyBindingValidator.validate(KeyEvent.VK_C, 0));
  }

  @Test
  void bareDigitIsRejected() {
    assertEquals(Result.NEEDS_MODIFIER,
        HotkeyBindingValidator.validate(KeyEvent.VK_2, 0));
  }

  @Test
  void bareEscapeIsRejected() {
    // Not a function key — Escape is far too load-bearing to grab globally.
    assertEquals(Result.NEEDS_MODIFIER,
        HotkeyBindingValidator.validate(KeyEvent.VK_ESCAPE, 0));
  }

  // ── rejected: modifier-only presses ──────────────────────────────────────

  @Test
  void modifierOnlyIsRejected() {
    assertEquals(Result.MODIFIER_ONLY,
        HotkeyBindingValidator.validate(KeyEvent.VK_SHIFT, KeyEvent.SHIFT_MASK));
    assertEquals(Result.MODIFIER_ONLY,
        HotkeyBindingValidator.validate(KeyEvent.VK_CONTROL, KeyEvent.CTRL_MASK));
    assertEquals(Result.MODIFIER_ONLY,
        HotkeyBindingValidator.validate(KeyEvent.VK_ALT, KeyEvent.ALT_MASK));
    assertEquals(Result.MODIFIER_ONLY,
        HotkeyBindingValidator.validate(KeyEvent.VK_META, KeyEvent.META_MASK));
  }

  @Test
  void modifierOnlyIsRejectedEvenWithOtherModifiersHeld() {
    assertEquals(Result.MODIFIER_ONLY,
        HotkeyBindingValidator.validate(KeyEvent.VK_SHIFT, SHIFT_META));
  }

  // ── accepted: function keys stand alone ──────────────────────────────────

  @Test
  void bareFunctionKeysAreAccepted() {
    // Dedicated keys — grabbing these costs no other application the ability to type.
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_F1, 0));
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_F12, 0));
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_F13, 0));
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_F24, 0));
  }

  // ── accepted: normal modified combinations, either convention ────────────

  @Test
  void legacyModifiedCombinationIsAccepted() {
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_C, SHIFT_META));
  }

  @Test
  void extendedModifiedCombinationIsAccepted() {
    // 320 — the value that appears in preferences migrated from SikuliX 2.0.x (#449).
    int extended = InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK;
    assertEquals(Result.OK, HotkeyBindingValidator.validate(KeyEvent.VK_C, extended));
  }

  // ── collision between the two hotkeys ────────────────────────────────────

  @Test
  void identicalBindingsCollide() {
    assertEquals(Result.DUPLICATE,
        HotkeyBindingValidator.validate(KeyEvent.VK_C, SHIFT_META, KeyEvent.VK_C, SHIFT_META));
  }

  @Test
  void sameKeyDifferentModifiersDoesNotCollide() {
    assertEquals(Result.OK,
        HotkeyBindingValidator.validate(KeyEvent.VK_C, SHIFT_META, KeyEvent.VK_C, KeyEvent.CTRL_MASK));
  }

  @Test
  void ownInvalidityIsReportedBeforeCollision() {
    // A bare letter that also happens to equal the other binding should report the
    // more actionable problem, not "duplicate".
    assertEquals(Result.NEEDS_MODIFIER,
        HotkeyBindingValidator.validate(KeyEvent.VK_C, 0, KeyEvent.VK_C, 0));
  }

  // ── message keys ─────────────────────────────────────────────────────────

  @Test
  void okHasNoMessageAndFailuresDo() {
    assertTrue(Result.OK.isOk());
    assertNull(Result.OK.getMessageKey());
    for (Result r : Result.values()) {
      if (r != Result.OK) {
        assertFalse(r.isOk(), r + " should not be OK");
        assertNotNull(r.getMessageKey(), r + " needs a message key");
      }
    }
  }
}
