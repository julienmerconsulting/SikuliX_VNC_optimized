/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import com.sikulix.ocr.OCREngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.sikuli.mcp.crypto.Hashing;
import org.sikuli.script.Region;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Confidential-mode counterpart of {@link ReadTextInRegionTool}: runs OCR
 * on a region, writes the recognised text to the local vault, and returns
 * only aggregate metadata (path, SHA-256, line count, character count)
 * to the LLM. The text content never crosses the tool boundary.
 *
 * <p>The LLM can pass the vault path to follow-up tools (archive, diff
 * against an expected reference, attach to a test report) without ever
 * having seen the content itself.
 *
 * <p>For a non-confidential workflow where the LLM must read what was
 * captured, keep using {@link ReadTextInRegionTool} — only one of the two
 * is registered at startup, depending on {@code OCULIX_MCP_MODE}.
 */
public final class OcrToDiskTool implements Tool {

  @Override public String name()        { return "oculix_ocr_to_disk"; }
  @Override public String description() {
    return "Run OCR on a region and write the recognised text to a local file. Returns {path, sha256, line_count, char_count, engine} — the text content is NOT returned to the LLM. Use in confidential mode when captured text must not leave the host.";
  }

  @Override public JSONObject inputSchema() {
    return new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
            .put("region", RegionSpec.jsonSchema())
            .put("label", new JSONObject()
                .put("type", "string")
                .put("description", "Optional human tag embedded in the filename")))
        .put("required", new JSONArray().put("region"));
  }

  @Override public JSONObject call(JSONObject args) throws Exception {
    Region region = RegionSpec.fromJson(args.getJSONObject("region"));
    File tmp = OcrHelper.captureToTempFile(region);
    try {
      OCREngine engine = OcrHelper.engine();
      String json = engine.recognize(tmp.getAbsolutePath());
      List<String> texts = engine.parseTexts(json);
      String joined = String.join("\n", texts);
      byte[] bytes = joined.getBytes(StandardCharsets.UTF_8);
      String sha = Hashing.sha256Hex(bytes);

      Path vault = VaultDir.ensure();
      String label = sanitizeLabel(args.optString("label", ""));
      String base = "ocr-" + Instant.now().toEpochMilli()
          + (label.isEmpty() ? "" : "-" + label) + "-" + sha.substring(0, 12) + ".txt";
      Path out = vault.resolve(base);
      Files.write(out, bytes);

      return Tool.textResult(new JSONObject()
          .put("saved", true)
          .put("path", out.toAbsolutePath().toString())
          .put("sha256", sha)
          .put("engine", engine.getName())
          .put("line_count", texts.size())
          .put("char_count", joined.length())
          .put("bytes", bytes.length)
          .toString());
    } finally {
      tmp.delete();
    }
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
