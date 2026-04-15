/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import org.json.JSONObject;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.script.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Confidential-mode counterpart of {@link ScreenshotTool}: captures the
 * screen (or a region) and writes the PNG to the local vault, returning
 * only metadata (path, SHA-256, dimensions) to the LLM.
 *
 * <p>No pixel data ever crosses the tool boundary — the LLM receives a
 * handle it can pass to downstream tools ("open this file", "archive
 * this capture", "attach this screenshot to the bug report") but cannot
 * inspect the image content.
 *
 * <p>This is the tool that makes the bank pitch defensible: the SHA-256
 * in the audit trail proves what was captured, the local file holds the
 * evidence, and the frontier LLM never sees any bank-internal pixel.
 */
public final class ScreenshotToDiskTool implements Tool {

  @Override public String name()        { return "oculix_screenshot_to_disk"; }
  @Override public String description() {
    return "Capture the screen (or a region) to a local PNG file. Returns {path, sha256, width, height} — image bytes are NOT returned to the LLM. Use in confidential mode when the captured content must not leave the host.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema()
                .put("description", "Optional region to capture. Full screen 0 if omitted."))
            .put("label", new JSONObject()
                .put("type", "string")
                .put("description", "Optional human tag embedded in the filename for later retrieval")));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    ScreenImage img;
    JSONObject region = args.optJSONObject("region");
    if (region != null && region.has("x") && region.has("y")
        && region.has("width") && region.has("height")) {
      Region r = RegionSpec.fromJson(region);
      img = r.getScreen().capture(r);
    } else {
      img = new Screen().capture();
    }
    BufferedImage bi = img.getImage();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bi, "png", baos);
    byte[] png = baos.toByteArray();
    String sha = Hashing.sha256Hex(png);

    Path vault = VaultDir.ensure();
    String label = sanitizeLabel(args.optString("label", ""));
    String base = "screenshot-" + Instant.now().toEpochMilli()
        + (label.isEmpty() ? "" : "-" + label) + "-" + sha.substring(0, 12) + ".png";
    Path out = vault.resolve(base);
    Files.write(out, png);

    return Tool.textResult(new JSONObject()
        .put("saved", true)
        .put("path", out.toAbsolutePath().toString())
        .put("sha256", sha)
        .put("width", bi.getWidth())
        .put("height", bi.getHeight())
        .put("bytes", png.length)
        .toString());
  }

  private static String sanitizeLabel(String raw) {
    if (raw == null) return "";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < raw.length() && sb.length() < 32; i++) {
      char c = raw.charAt(i);
      if (Character.isLetterOrDigit(c) || c == '-' || c == '_') sb.append(c);
      else if (Character.isWhitespace(c)) sb.append('_');
    }
    return sb.toString();
  }
}
