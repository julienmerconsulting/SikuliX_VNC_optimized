/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONObject;
import org.sikuli.script.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Capture the whole screen or a sub-region, returning a base64 PNG to the
 * MCP client (via an {@code image} content block).
 * @author Julien Mer (julienmerconsulting)
 * @author Claude (Anthropic)
 * @since 3.0.3
 */
public final class ScreenshotTool implements Tool {

  @Override public String name()        { return "oculix_screenshot"; }
  @Override public String description() {
    return "Capture the screen (or a region) and return it as a PNG image the LLM can see.";
  }

  @Override public JSONObject inputSchema() {
    int nScreens = Screen.getNumberScreens();
    String screenDesc = "Screen index to capture (0-based). "
        + "Defaults to 0 (primary). This machine currently exposes " + nScreens
        + " screen" + (nScreens == 1 ? "" : "s") + ". Ignored when 'region' is provided "
        + "(the region already carries its own screen).";
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to capture. If omitted, captures the full screen at 'screen_index'."))
            .put("screen_index", new JSONObject()
                .put("type", "integer")
                .put("minimum", 0)
                .put("description", screenDesc)));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    ScreenImage img;
    JSONObject region = args.optJSONObject("region");
    if (region != null && region.has("x") && region.has("y")
        && region.has("width") && region.has("height")) {
      Region r = RegionSpec.fromJson(region);
      img = r.getScreen().capture(r);
    } else {
      // No region (or empty object sent by some clients) — capture full screen at screen_index.
      // JSON-RPC callers sometimes serialise integers as strings, so accept both.
      int idx = args.optInt("screen_index", -1);
      if (idx < 0) {
        String s = args.optString("screen_index", "").trim();
        if (!s.isEmpty()) {
          try { idx = Integer.parseInt(s); } catch (NumberFormatException ignore) {}
        }
      }
      if (idx < 0) idx = 0;
      int nScreens = Screen.getNumberScreens();
      if (idx >= nScreens) {
        throw new IllegalArgumentException("screen_index " + idx + " out of range; this machine has "
            + nScreens + " screen" + (nScreens == 1 ? "" : "s") + " (valid indices: 0.." + (nScreens - 1) + ")");
      }
      img = new Screen(idx).capture();
    }
    BufferedImage bi = img.getImage();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

    JSONObject meta = new JSONObject()
        .put("width", bi.getWidth())
        .put("height", bi.getHeight());
    return Tool.imageResult(b64, meta.toString());
  }
}
