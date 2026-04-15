/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Local "vault" directory where confidential-mode tools drop their content
 * (OCR text, PNG screenshots) instead of returning it inline to the LLM.
 *
 * <p>Location, in priority order:
 * <ol>
 *   <li>Env var {@code OCULIX_MCP_VAULT} if set</li>
 *   <li>Otherwise {@code ~/.oculix-mcp/vault/}</li>
 * </ol>
 *
 * <p>Permissions are tightened to {@code 700} on POSIX so that other users
 * on the host cannot browse what the agent has captured. The LLM never
 * receives file contents — only a hash, a path, and minimal metadata —
 * but anyone with shell access to the machine obviously can read the files,
 * so the vault is not a secret store, it's a local-only landing zone.
 */
public final class VaultDir {

  public static final String ENV_OVERRIDE = "OCULIX_MCP_VAULT";

  private VaultDir() {}

  public static Path resolve() {
    String override = System.getenv(ENV_OVERRIDE);
    if (override != null && !override.isBlank()) {
      return Paths.get(override);
    }
    return Paths.get(System.getProperty("user.home"), ".oculix-mcp", "vault");
  }

  public static Path ensure() throws IOException {
    Path dir = resolve();
    Files.createDirectories(dir);
    try {
      Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
      Files.setPosixFilePermissions(dir, perms);
    } catch (UnsupportedOperationException ignored) {
      // non-POSIX (Windows) — rely on user ACLs
    }
    return dir;
  }
}
