/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory registry of the tools exposed by the MCP server.
 *
 * <p>Registration order is preserved so that {@code tools/list} returns
 * the tools in a stable, readable order.
 *
 * <p>The registry is picked at startup according to the
 * {@code OCULIX_MCP_MODE} environment variable:
 * <dl>
 *   <dt>{@code open} (default)</dt>
 *   <dd>9 tools including {@code oculix_screenshot} and
 *       {@code oculix_read_text_in_region}, which return image/text content
 *       inline to the LLM. Suited to Claude Desktop / dev workflows where
 *       the LLM is trusted to see the screen.</dd>
 *
 *   <dt>{@code confidential}</dt>
 *   <dd>Same 7 outcome-only tools plus {@code oculix_screenshot_to_disk}
 *       and {@code oculix_ocr_to_disk}. The two content-leaking tools are
 *       <em>not</em> registered — the LLM cannot even see them in
 *       {@code tools/list}. No filter to bypass: the capability is
 *       physically absent from the surface.</dd>
 * </dl>
 *
 * <p>This is the mechanism that lets operators promise a regulated
 * customer: "no bank-internal screen content ever reaches the LLM".
 */
public final class ToolRegistry {

  /** Registry mode — see class-level javadoc. */
  public enum Mode {
    OPEN,
    CONFIDENTIAL;

    public static Mode fromEnv() {
      String raw = System.getenv("OCULIX_MCP_MODE");
      return parse(raw);
    }

    public static Mode parse(String raw) {
      if (raw == null || raw.isBlank()) return OPEN;
      switch (raw.trim().toLowerCase(Locale.ROOT)) {
        case "confidential": case "redact": case "private":
          return CONFIDENTIAL;
        case "open": case "":
          return OPEN;
        default:
          throw new IllegalArgumentException(
              "Unknown OCULIX_MCP_MODE=" + raw
                  + " (expected 'open' or 'confidential')");
      }
    }
  }

  private final Map<String, Tool> tools = new LinkedHashMap<>();
  private final Mode mode;

  public ToolRegistry() { this(Mode.OPEN); }

  public ToolRegistry(Mode mode) { this.mode = mode; }

  public Mode mode() { return mode; }

  public void register(Tool tool) {
    if (tools.containsKey(tool.name())) {
      throw new IllegalStateException("Duplicate tool name: " + tool.name());
    }
    tools.put(tool.name(), tool);
  }

  public Tool get(String name) {
    return tools.get(name);
  }

  public JSONArray listAsJson() {
    JSONArray arr = new JSONArray();
    for (Tool t : tools.values()) {
      arr.put(new JSONObject()
          .put("name", t.name())
          .put("description", t.description())
          .put("inputSchema", t.inputSchema()));
    }
    return arr;
  }

  public int size() { return tools.size(); }

  /** Default registry, mode resolved from the environment. */
  public static ToolRegistry defaultRegistry() {
    return defaultRegistry(Mode.fromEnv());
  }

  /** Default registry for an explicit mode — useful for tests. */
  public static ToolRegistry defaultRegistry(Mode mode) {
    ToolRegistry r = new ToolRegistry(mode);

    // Outcome-only tools — always safe, registered in both modes.
    r.register(new FindImageTool());
    r.register(new ClickImageTool());
    r.register(new ExistsImageTool());
    r.register(new WaitForImageTool());
    r.register(new TypeTextTool());
    r.register(new KeyComboTool());
    r.register(new FindTextTool());

    // Content-bearing vs. vault-only variants — mutually exclusive.
    if (mode == Mode.OPEN) {
      r.register(new ScreenshotTool());
      r.register(new ReadTextInRegionTool());
    } else {
      r.register(new ScreenshotToDiskTool());
      r.register(new OcrToDiskTool());
    }
    return r;
  }
}
