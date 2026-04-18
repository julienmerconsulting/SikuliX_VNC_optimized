/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cross-platform application launcher for local and remote (VNC via SSH) apps.
 * Handles X Server lifecycle, DISPLAY resolution, and SSH+VNC command building.
 */
public class AppLauncher {

  private static final String VCXSRV_PROCESS = "vcxsrv.exe";
  private static final String VCXSRV_PATH = "C:\\Program Files\\VcXsrv\\vcxsrv.exe";
  private static final int XSERVER_STARTUP_DELAY = 5000;
  private static final int LAUNCH_TIMEOUT_SECONDS = 120;

  private static final String DEFAULT_SSH_OPTIONS =
      "-Y -C -o Ciphers=aes128-ctr -o MACs=hmac-sha2-256,hmac-sha1 " +
      "-o TCPKeepAlive=no -o ServerAliveInterval=30 -o ServerAliveCountMax=3";

  // ═══════════════════════════════════════════════════════════════════════
  // PUBLIC API
  // ═══════════════════════════════════════════════════════════════════════

  /**
   * Launch a local application.
   */
  public static void launchLocal(String appPath) throws IOException {
    if (Commons.runningWindows()) {
      CommandExecutor.execFireAndForget(new String[]{"cmd", "/c", "start", "", appPath});
    } else if (Commons.runningMac()) {
      CommandExecutor.execFireAndForget(new String[]{"open", "-a", appPath});
    } else {
      CommandExecutor.execFireAndForget(appPath);
    }
  }

  /**
   * Launch a remote app via VNC over SSH with X11 forwarding.
   * Blocks until the VNC window appears or timeout (2 min).
   *
   * @throws TimeoutException if VNC window doesn't appear within timeout
   */
  public static void launchRemoteVNC(String host, String user, String password,
                                      String vncPort) throws Exception {
    ensureXServerRunning();

    String displayIp = resolveDisplayIp();
    String command = new VncCommandBuilder()
        .displayIp(displayIp)
        .credentials(user, password)
        .targetIp(host)
        .vncPort(vncPort != null ? vncPort : "5900")
        .sshOptions(DEFAULT_SSH_OPTIONS)
        .build();

    CommandExecutor.execFireAndForget(command);

    waitForVncWindow();
  }

  /**
   * Launch remote VNC with default port 5900.
   */
  public static void launchRemoteVNC(String host, String user, String password) throws Exception {
    launchRemoteVNC(host, user, password, "5900");
  }

  // ═══════════════════════════════════════════════════════════════════════
  // X SERVER
  // ═══════════════════════════════════════════════════════════════════════

  static void ensureXServerRunning() throws IOException {
    if (Commons.runningLinux()) return;

    if (Commons.runningWindows()) {
      if (!CommandExecutor.isProcessRunning(VCXSRV_PROCESS)) {
        CommandExecutor.execFireAndForget(
            String.format("\"%s\" -ac -multiwindow -clipboard -wgl", VCXSRV_PATH));
        sleep(XSERVER_STARTUP_DELAY);
      }
    } else if (Commons.runningMac()) {
      if (!CommandExecutor.isProcessRunning("XQuartz")) {
        CommandExecutor.execFireAndForget(new String[]{"open", "-a", "XQuartz"});
        sleep(XSERVER_STARTUP_DELAY);
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // DISPLAY IP RESOLUTION
  // ═══════════════════════════════════════════════════════════════════════

  static String resolveDisplayIp() throws IOException {
    if (!Commons.runningWindows()) return "localhost";

    return getWslIpAddress()
        .orElseThrow(() -> new IOException("Cannot determine WSL IP for DISPLAY variable."));
  }

  private static Optional<String> getWslIpAddress() throws IOException {
    Pattern ipPattern = Pattern.compile("IPv4.*?:\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    boolean inWslSection = false;

    try (Stream<String> lines = CommandExecutor.execLocal("ipconfig")) {
      for (String line : (Iterable<String>) lines::iterator) {
        if (line.contains("WSL")) inWslSection = true;
        if (inWslSection) {
          Matcher m = ipPattern.matcher(line);
          if (m.find()) return Optional.of(m.group(1));
        }
      }
    }
    return Optional.empty();
  }

  // ═══════════════════════════════════════════════════════════════════════
  // VNC WINDOW DETECTION WITH TIMEOUT
  // ═══════════════════════════════════════════════════════════════════════

  private static void waitForVncWindow() throws TimeoutException {
    ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "VncWindowWait");
      t.setDaemon(true);
      return t;
    });

    Future<?> future = executor.submit(() -> {
      while (!Thread.currentThread().isInterrupted()) {
        if (CommandExecutor.isProcessRunning("vncviewer")) return;
        sleep(1000);
      }
    });

    try {
      future.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      future.cancel(true);
      throw new TimeoutException(
          "VNC window did not appear within " + LAUNCH_TIMEOUT_SECONDS + " seconds. "
          + "Check SSH credentials, network, and X Server.");
    } catch (Exception e) {
      throw new RuntimeException("VNC launch failed: " + e.getMessage(), e);
    } finally {
      executor.shutdownNow();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // VNC COMMAND BUILDER
  // ═══════════════════════════════════════════════════════════════════════

  public static class VncCommandBuilder {
    private String displayIp;
    private String sshUser;
    private String sshPassword;
    private String targetIp;
    private String vncPort = "5900";
    private String sshOptions = "";

    public VncCommandBuilder displayIp(String ip) { this.displayIp = ip; return this; }
    public VncCommandBuilder credentials(String user, String pass) {
      this.sshUser = user; this.sshPassword = pass; return this;
    }
    public VncCommandBuilder targetIp(String ip) { this.targetIp = ip; return this; }
    public VncCommandBuilder vncPort(String port) { this.vncPort = port; return this; }
    public VncCommandBuilder sshOptions(String opts) { this.sshOptions = opts; return this; }

    public String build() {
      String vnc = String.format("vncviewer -FullColor localhost:%s", vncPort);
      String ssh = String.format("sshpass -p '%s' ssh %s %s@%s '%s'",
          sshPassword, sshOptions, sshUser, targetIp, vnc);

      if (Commons.runningWindows()) {
        return String.format(
            "cmd /k start /min C:\\Windows\\System32\\wsl.exe bash -c \"DISPLAY=%s:0.0 %s\"",
            displayIp, ssh);
      } else {
        return String.format("DISPLAY=%s:0.0 %s", displayIp, ssh);
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════════
  // UTIL
  // ═══════════════════════════════════════════════════════════════════════

  private static void sleep(int millis) {
    try { Thread.sleep(millis); } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
