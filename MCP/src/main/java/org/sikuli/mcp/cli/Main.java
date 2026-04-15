/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.mcp.cli;

import org.sikuli.mcp.audit.JournalVerifier;
import org.sikuli.mcp.audit.JournalWriter;
import org.sikuli.mcp.crypto.KeyManager;
import org.sikuli.mcp.gate.AutoApproveGate;
import org.sikuli.mcp.server.McpDispatcher;
import org.sikuli.mcp.server.McpServer;
import org.sikuli.mcp.server.SessionStore;
import org.sikuli.mcp.server.StartupCheck;
import org.sikuli.mcp.tools.ToolRegistry;
import org.sikuli.mcp.transport.BearerAuth;
import org.sikuli.mcp.transport.HttpTransport;

import java.nio.file.*;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entry point for the oculix-mcp executable jar.
 *
 * <p>Usage:
 * <pre>
 *   java -jar oculix-mcp-server.jar run          # (default) start the MCP server over stdio
 *   java -jar oculix-mcp-server.jar rotate-key   # rotate the Ed25519 audit signing key
 *   java -jar oculix-mcp-server.jar recover      # record an unsigned-gap and start fresh
 *   java -jar oculix-mcp-server.jar verify FILE  # verify a journal file
 *   java -jar oculix-mcp-server.jar --help
 * </pre>
 */
public final class Main {

  // Rotation thresholds — can be tuned later via env vars
  private static final long MAX_ENTRIES_PER_FILE = 10_000L;
  private static final long MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L; // 24h

  public static void main(String[] rawArgs) throws Exception {
    String[] args = rawArgs == null ? new String[0] : rawArgs;
    String cmd = args.length == 0 ? "run" : args[0];
    String[] rest = args.length <= 1 ? new String[0]
                                     : Arrays.copyOfRange(args, 1, args.length);

    switch (cmd) {
      case "run":         cmdRun(); break;
      case "serve":       cmdServe(rest); break;
      case "rotate-key":  cmdRotateKey(); break;
      case "recover":     cmdRecover(); break;
      case "verify":      cmdVerify(rest); break;
      case "--help":
      case "-h":
      case "help":        printUsage(); break;
      default:
        System.err.println("Unknown command: " + cmd);
        printUsage();
        System.exit(1);
    }
  }

  // ── run ──

  private static void cmdRun() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    KeyManager keys;
    try {
      keys = StartupCheck.validateAndLoad(oculixDir, journalDir);
    } catch (StartupCheck.StartupException e) {
      System.err.println("[oculix-mcp] Startup refused:\n" + e.getMessage());
      System.exit(3);
      return;
    }

    try (JournalWriter journal = new JournalWriter(journalDir, keys,
             MAX_ENTRIES_PER_FILE, MAX_AGE_MILLIS)) {
      McpServer server = new McpServer(
          ToolRegistry.defaultRegistry(),
          new AutoApproveGate(),
          journal,
          System.in, System.out);
      server.run();
    }
  }

  // ── serve (HTTP) ──

  /**
   * Start the MCP server over HTTP (Streamable HTTP transport).
   *
   * <p>Flags:
   * <ul>
   *   <li>{@code --host HOST} (default {@code 127.0.0.1})</li>
   *   <li>{@code --port PORT} (default {@code 7337}, {@code 0} to let the OS pick)</li>
   * </ul>
   *
   * <p>The bearer token is read from {@code OCULIX_MCP_TOKEN}; if absent,
   * one is generated and printed on startup. Never generate tokens silently
   * in production — set the env var explicitly.
   */
  private static void cmdServe(String[] args) throws Exception {
    String host = "127.0.0.1";
    int port = 7337;
    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      if ("--host".equals(a) && i + 1 < args.length) { host = args[++i]; }
      else if ("--port".equals(a) && i + 1 < args.length) {
        port = Integer.parseInt(args[++i]);
      } else if (a.startsWith("--host=")) { host = a.substring("--host=".length()); }
      else if (a.startsWith("--port=")) {
        port = Integer.parseInt(a.substring("--port=".length()));
      } else {
        System.err.println("Unknown serve flag: " + a);
        System.exit(1);
      }
    }

    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    KeyManager keys;
    try {
      keys = StartupCheck.validateAndLoad(oculixDir, journalDir);
    } catch (StartupCheck.StartupException e) {
      System.err.println("[oculix-mcp] Startup refused:\n" + e.getMessage());
      System.exit(3);
      return;
    }

    String token = System.getenv("OCULIX_MCP_TOKEN");
    boolean generated = false;
    if (token == null || token.isBlank()) {
      token = BearerAuth.generateToken();
      generated = true;
    }
    BearerAuth auth = new BearerAuth(token);

    ToolRegistry.Mode mode = ToolRegistry.Mode.fromEnv();
    ToolRegistry registry = ToolRegistry.defaultRegistry(mode);

    try (JournalWriter journal = new JournalWriter(journalDir, keys,
             MAX_ENTRIES_PER_FILE, MAX_AGE_MILLIS)) {
      McpDispatcher dispatcher = new McpDispatcher(
          registry, new AutoApproveGate(), journal);
      SessionStore sessions = new SessionStore();
      HttpTransport http = new HttpTransport(dispatcher, sessions, auth, host, port);
      http.start();

      int bound = http.boundPort();
      System.err.println("[oculix-mcp] listening on http://" + host + ":" + bound + "/mcp");
      System.err.println("[oculix-mcp] mode=" + mode.name().toLowerCase()
          + " tools=" + registry.size());
      if (generated) {
        System.err.println("[oculix-mcp] OCULIX_MCP_TOKEN not set — generated one-shot token:");
        System.err.println("[oculix-mcp]   " + token);
        System.err.println("[oculix-mcp] Persist it as an env var to avoid regenerating on restart.");
      } else {
        System.err.println("[oculix-mcp] bearer token: from OCULIX_MCP_TOKEN env var");
      }

      // Block the main thread; shutdown on Ctrl-C / SIGTERM.
      Thread shutdown = new Thread(() -> {
        try { http.close(); } catch (Exception ignored) {}
      }, "oculix-mcp-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdown);

      // Park forever.
      synchronized (http) { http.wait(); }
    }
  }

  // ── rotate-key ──

  private static void cmdRotateKey() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();
    Path privKey = oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME);
    Path pubKey  = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);

    if (!Files.exists(privKey) || !Files.exists(pubKey)) {
      System.err.println("No existing key pair to rotate in " + oculixDir);
      System.exit(4);
    }

    KeyManager oldKeys = KeyManager.loadExisting(privKey, pubKey);

    // Generate the new pair in a temp dir first
    Path stagingDir = oculixDir.resolve("staging-" + System.currentTimeMillis());
    KeyManager newKeys = KeyManager.generateAndStore(stagingDir);

    // Write rotation_end marker to the current journal, signed by the OLD key
    try (JournalWriter journal = new JournalWriter(journalDir, oldKeys,
             Long.MAX_VALUE, Long.MAX_VALUE)) {
      journal.rotate("manual_rotation", newKeys.publicKeySha256Hex());
    }

    // Archive the old key pair
    Path archiveDir = oculixDir.resolve("archive");
    Files.createDirectories(archiveDir);
    String ts = String.valueOf(System.currentTimeMillis());
    Files.move(privKey, archiveDir.resolve("private-" + ts + ".key"));
    Files.move(pubKey, archiveDir.resolve("public-" + ts + ".key"));

    // Promote the new pair
    Files.move(stagingDir.resolve(KeyManager.PRIVATE_KEY_FILENAME), privKey);
    Files.move(stagingDir.resolve(KeyManager.PUBLIC_KEY_FILENAME), pubKey);
    KeyManager.restrictPrivate(privKey);
    Files.deleteIfExists(stagingDir);

    System.out.println("Key rotated successfully.");
    System.out.println("Old key archived to " + archiveDir);
    System.out.println("New public key SHA-256: " + newKeys.publicKeySha256Hex());
  }

  // ── recover ──

  private static void cmdRecover() throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path journalDir = StartupCheck.defaultJournalDir();

    Files.createDirectories(journalDir);
    String ts = String.valueOf(System.currentTimeMillis());
    Path gap = journalDir.resolve("UNSIGNED_GAP-" + ts + ".txt");
    Files.writeString(gap,
        "Audit chain broken at " + java.time.Instant.now() + ".\n"
            + "Reason: operator-initiated recovery (key lost or chain corruption).\n"
            + "Subsequent entries start a fresh chain under a new Ed25519 key pair.\n",
        StandardOpenOption.CREATE_NEW);

    // Wipe existing keys if any — recovery means we accept the break
    Path privKey = oculixDir.resolve(KeyManager.PRIVATE_KEY_FILENAME);
    Path pubKey  = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);
    Files.deleteIfExists(privKey);
    Files.deleteIfExists(pubKey);

    // Generate a fresh pair
    KeyManager fresh = KeyManager.generateAndStore(oculixDir);

    System.out.println("Recovery complete.");
    System.out.println("Unsigned gap marker: " + gap);
    System.out.println("Fresh key pair generated.");
    System.out.println("New public key SHA-256: " + fresh.publicKeySha256Hex());
  }

  // ── verify ──

  private static void cmdVerify(String[] args) throws Exception {
    Path oculixDir = StartupCheck.defaultOculixDir();
    Path pubKeyPath = oculixDir.resolve(KeyManager.PUBLIC_KEY_FILENAME);

    List<Path> files;
    if (args.length == 0) {
      Path journalDir = StartupCheck.defaultJournalDir();
      if (!Files.isDirectory(journalDir)) {
        System.err.println("No journal directory at " + journalDir);
        System.exit(5);
        return;
      }
      try (Stream<Path> s = Files.list(journalDir)) {
        files = s.filter(p -> p.getFileName().toString().startsWith("audit-")
                           && p.getFileName().toString().endsWith(".jsonl"))
                 .sorted(Comparator.naturalOrder())
                 .collect(Collectors.toList());
      }
    } else {
      files = Arrays.stream(args).map(Paths::get).collect(Collectors.toList());
    }

    PublicKey pub = JournalVerifier.loadPublicKey(pubKeyPath);
    boolean allOk = true;
    for (Path f : files) {
      JournalVerifier.Result r = JournalVerifier.verify(f, pub);
      if (r.ok) {
        System.out.println("OK  " + f + "  (" + r.entriesChecked + " entries)");
      } else {
        allOk = false;
        System.out.println("FAIL " + f + "  (" + r.entriesChecked + " entries)");
        for (String issue : r.issues) {
          System.out.println("     " + issue);
        }
      }
    }
    if (!allOk) System.exit(6);
  }

  private static void printUsage() {
    System.out.println("oculix-mcp — Auditable visual-control MCP server for regulated environments");
    System.out.println();
    System.out.println("Usage:");
    System.out.println("  oculix-mcp run          Start the MCP server over stdio (default)");
    System.out.println("  oculix-mcp serve        Start the MCP server over HTTP (Streamable HTTP)");
    System.out.println("                          Flags: --host HOST (default 127.0.0.1)");
    System.out.println("                                 --port PORT (default 7337, 0 for auto)");
    System.out.println("                          Env:   OCULIX_MCP_TOKEN (bearer auth, generated if absent)");
    System.out.println("                                 OCULIX_MCP_MODE=open|confidential");
    System.out.println("                                 OCULIX_MCP_VAULT=<path> (confidential mode landing dir)");
    System.out.println("  oculix-mcp rotate-key   Rotate the Ed25519 audit signing key");
    System.out.println("  oculix-mcp recover      Record an unsigned gap and start a fresh chain");
    System.out.println("  oculix-mcp verify [FILES...]");
    System.out.println("                          Verify journal files (all by default)");
    System.out.println();
    System.out.println("Config dir: " + StartupCheck.defaultOculixDir());
    System.out.println("Journal dir: " + StartupCheck.defaultJournalDir());
  }
}
