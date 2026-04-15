/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp;

import org.junit.jupiter.api.Test;
import org.sikuli.mcp.tools.ToolRegistry;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryModeTest {

  @Test
  void openModeExposesContentBearingTools() {
    ToolRegistry r = ToolRegistry.defaultRegistry(ToolRegistry.Mode.OPEN);
    assertNotNull(r.get("oculix_screenshot"));
    assertNotNull(r.get("oculix_read_text_in_region"));
    // to_disk variants must NOT be exposed in open mode.
    assertNull(r.get("oculix_screenshot_to_disk"));
    assertNull(r.get("oculix_ocr_to_disk"));
  }

  @Test
  void confidentialModeHidesContentBearingTools() {
    ToolRegistry r = ToolRegistry.defaultRegistry(ToolRegistry.Mode.CONFIDENTIAL);
    // This is the hard guarantee: the fuming tools do NOT exist in the registry.
    // An LLM cannot call what it cannot see in tools/list.
    assertNull(r.get("oculix_screenshot"),
        "screenshot must be absent in confidential mode");
    assertNull(r.get("oculix_read_text_in_region"),
        "read_text_in_region must be absent in confidential mode");
    // to_disk variants must be present.
    assertNotNull(r.get("oculix_screenshot_to_disk"));
    assertNotNull(r.get("oculix_ocr_to_disk"));
  }

  @Test
  void bothModesExposeOutcomeOnlyTools() {
    for (ToolRegistry.Mode mode : ToolRegistry.Mode.values()) {
      ToolRegistry r = ToolRegistry.defaultRegistry(mode);
      assertNotNull(r.get("oculix_click_image"), mode + " missing click_image");
      assertNotNull(r.get("oculix_type_text"), mode + " missing type_text");
      assertNotNull(r.get("oculix_key_combo"), mode + " missing key_combo");
      assertNotNull(r.get("oculix_find_image"), mode + " missing find_image");
      assertNotNull(r.get("oculix_exists_image"), mode + " missing exists_image");
      assertNotNull(r.get("oculix_wait_for_image"), mode + " missing wait_for_image");
      assertNotNull(r.get("oculix_find_text"), mode + " missing find_text");
    }
  }

  @Test
  void registrySizeMatchesExpectedCount() {
    assertEquals(9, ToolRegistry.defaultRegistry(ToolRegistry.Mode.OPEN).size());
    assertEquals(9, ToolRegistry.defaultRegistry(ToolRegistry.Mode.CONFIDENTIAL).size());
  }

  @Test
  void parseTolerant() {
    assertEquals(ToolRegistry.Mode.OPEN, ToolRegistry.Mode.parse(null));
    assertEquals(ToolRegistry.Mode.OPEN, ToolRegistry.Mode.parse(""));
    assertEquals(ToolRegistry.Mode.OPEN, ToolRegistry.Mode.parse("open"));
    assertEquals(ToolRegistry.Mode.OPEN, ToolRegistry.Mode.parse("OPEN"));
    assertEquals(ToolRegistry.Mode.CONFIDENTIAL, ToolRegistry.Mode.parse("confidential"));
    assertEquals(ToolRegistry.Mode.CONFIDENTIAL, ToolRegistry.Mode.parse("redact"));
    assertEquals(ToolRegistry.Mode.CONFIDENTIAL, ToolRegistry.Mode.parse("private"));
    assertThrows(IllegalArgumentException.class,
        () -> ToolRegistry.Mode.parse("bogus"));
  }
}
