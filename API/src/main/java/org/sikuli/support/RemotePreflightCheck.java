/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-flight checks before launching a remote VNC session.
 * Verifies: sshpass, X Server, SSH fingerprint, vncviewer.
 */
public class RemotePreflightCheck {

  public static class CheckResult {
    public final String name;
    public final boolean passed;
    public final String message;
    public final Runnable autoFix;

    CheckResult(String name, boolean passed, String message, Runnable autoFix) {
      this.name = name;
      this.passed = passed;
      this.message = message;
      this.autoFix = autoFix;
    }
  }

  /**
   * Run all pre-flight checks for remote VNC connection.
   */
  public static List<CheckResult> runAll(String targetHost) {
    List<CheckResult> results = new ArrayList<>();
    results.add(checkSshpass());
    results.add(checkXServer());
    results.add(checkVncViewer());
    results.add(checkFingerprint(targetHost));
    return results;
  }

  public static boolean allPassed(List<CheckResult> results) {
    return results.stream().allMatch(r -> r.passed);
  }

  static CheckResult checkSshpass() {
    boolean found = CommandExecutor.isToolAvailable("sshpass");
    if (found) {
      return new CheckResult("sshpass", true, "sshpass found", null);
    }
    return new CheckResult("sshpass", false,
        "sshpass not installed — required for password-based SSH",
        () -> CommandExecutor.aptInstall("sshpass"));
  }

  static CheckResult checkXServer() {
    if (Commons.runningLinux()) {
      return new CheckResult("X Server", true, "Native X display (Linux)", null);
    }

    boolean running;
    String serverName;
    String searchQuery;

    if (Commons.runningWindows()) {
      serverName = "VcXsrv";
      searchQuery = "install VcXsrv Windows";
      running = CommandExecutor.isProcessRunning("vcxsrv.exe");
    } else {
      serverName = "XQuartz";
      searchQuery = "install XQuartz macOS";
      running = CommandExecutor.isProcessRunning("XQuartz");
    }

    if (running) {
      return new CheckResult("X Server", true, serverName + " running", null);
    }

    return new CheckResult("X Server", false,
        serverName + " not running — required for VNC display",
        () -> {
          try {
            Desktop.getDesktop().browse(
                new URI("https://www.google.com/search?q=" + searchQuery.replace(" ", "+")));
          } catch (Exception ignored) {}
        });
  }

  static CheckResult checkVncViewer() {
    boolean found = CommandExecutor.isToolAvailable("vncviewer");
    if (found) {
      return new CheckResult("vncviewer", true, "vncviewer found", null);
    }

    String installHint;
    Runnable fix;
    if (Commons.runningMac()) {
      installHint = "vncviewer not found — install via: brew install tiger-vnc";
      fix = () -> {
        try {
          Desktop.getDesktop().browse(
              new URI("https://www.google.com/search?q=install+tigervnc+viewer+macos+homebrew"));
        } catch (Exception ignored) {}
      };
    } else if (Commons.runningWindows()) {
      installHint = "vncviewer not found in WSL — install via: apt install tigervnc-viewer";
      fix = () -> CommandExecutor.aptInstall("tigervnc-viewer");
    } else {
      installHint = "vncviewer not found — install via: apt install tigervnc-viewer";
      fix = () -> CommandExecutor.aptInstall("tigervnc-viewer");
    }

    return new CheckResult("vncviewer", false, installHint, fix);
  }

  static CheckResult checkFingerprint(String host) {
    if (host == null || host.isEmpty()) {
      return new CheckResult("SSH fingerprint", true, "No host specified", null);
    }
    boolean known = CommandExecutor.isSshHostKnown(host);
    if (known) {
      return new CheckResult("SSH fingerprint", true,
          host + " found in known_hosts", null);
    }
    return new CheckResult("SSH fingerprint", false,
        host + " not in known_hosts",
        () -> CommandExecutor.addSshFingerprint(host));
  }
}
