/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.script;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code Key.convertKeyToText()} renders a hotkey for display — in the Preferences
 * dialog and in the Run / Record / Capture tooltips.
 *
 * <p>It delegates to {@code KeyEvent.getKeyModifiersText()}, which understands only
 * the legacy {@code InputEvent.*_MASK} bits. A stored modifier in the extended
 * {@code *_DOWN_MASK} convention (320 = Shift+Meta, as inherited from SikuliX 2.0.x —
 * see #449) therefore used to render as no modifiers at all, displaying a working
 * "Shift+Cmd+C" binding as a bare "C".
 */
class KeyConvertKeyToTextTest {

  @Test
  void legacyModifiersRender() {
    String text = Key.convertKeyToText(KeyEvent.VK_C, KeyEvent.SHIFT_MASK + KeyEvent.META_MASK);
    assertTrue(text.endsWith("C"), "should name the key, was: " + text);
    assertTrue(text.length() > 1, "should render modifiers, was: " + text);
  }

  @Test
  void extendedModifiersRenderTheSameAsLegacy() {
    // The exact pair behind the bug: 5 (legacy) and 320 (extended) mean the same
    // combination and must display identically.
    String legacy = Key.convertKeyToText(KeyEvent.VK_C,
        KeyEvent.SHIFT_MASK + KeyEvent.META_MASK);
    String extended = Key.convertKeyToText(KeyEvent.VK_C,
        InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK);
    assertEquals(legacy, extended);
  }

  @Test
  void extendedModifiersAreNotSilentlyDropped() {
    // The regression itself: 320 must not render as a bare "C".
    String text = Key.convertKeyToText(KeyEvent.VK_C,
        InputEvent.SHIFT_DOWN_MASK + InputEvent.META_DOWN_MASK);
    assertNotEquals("C", text.trim(),
        "extended modifiers were dropped — a compound hotkey renders as a bare key");
  }

  @Test
  void extendedShiftAltRendersLikeItsLegacyEquivalent() {
    // The Linux/Windows default stop hotkey, in both conventions.
    String legacy = Key.convertKeyToText(KeyEvent.VK_C,
        KeyEvent.SHIFT_MASK + KeyEvent.ALT_MASK);
    String extended = Key.convertKeyToText(KeyEvent.VK_C,
        InputEvent.SHIFT_DOWN_MASK + InputEvent.ALT_DOWN_MASK);
    assertEquals(legacy, extended);
  }

  @Test
  void noModifiersRendersJustTheKey() {
    assertEquals("C", Key.convertKeyToText(KeyEvent.VK_C, 0).trim());
  }
}
