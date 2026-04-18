/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Cross-platform command execution: local, WSL, PowerShell, SSH.
 */
public class CommandExecutor {

  /**
   * Execute a local command and return stdout as a stream of lines.
   * On Windows uses cmd /c, on Unix uses bash -c.
   */
  public static Stream<String> execLocal(String command) throws IOException {
    String[] cmd;
    if (Commons.runningWindows()) {
      cmd = new String[]{"cmd", "/c", command};
    } else {
      cmd = new String[]{"bash", "-c", command};
    }
    Process process = Runtime.getRuntime().exec(cmd);
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return reader.lines().onClose(() -> {
      try { reader.close(); } catch (IOException ignored) {}
      process.destroy();
    });
  }

  /**
   * Execute a command via WSL (Windows only).
   */
  public static Stream<String> execWsl(String command) throws IOException {
    Process process = Runtime.getRuntime().exec(
        new String[]{"wsl.exe", "bash", "-c", command});
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return reader.lines().onClose(() -> {
      try { reader.close(); } catch (IOException ignored) {}
      process.destroy();
    });
  }

  /**
   * Execute a PowerShell command.
   * Uses 'powershell' on Windows, 'pwsh' on Mac/Linux.
   */
  public static Stream<String> execPowershell(String command) throws IOException {
    String ps = Commons.runningWindows() ? "powershell" : "pwsh";
    Process process = Runtime.getRuntime().exec(
        new String[]{ps, "-Command", command});
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    return reader.lines().onClose(() -> {
      try { reader.close(); } catch (IOException ignored) {}
      process.destroy();
    });
  }

  /**
   * Execute a remote command via SSH with sshpass for password auth.
   * On Windows goes through WSL, on Unix direct bash.
   */
  public static String execRemoteSSH(String host, String user, String password,
                                      String remoteCmd, String sshOptions) throws Exception {
    String escaped = remoteCmd.replace("'", "'\\''");
    String sshCmd = String.format("sshpass -p '%s' ssh %s %s@%s '%s'",
        password, sshOptions != null ? sshOptions : "", user, host, escaped);

    String fullCmd;
    if (Commons.runningWindows()) {
      fullCmd = "wsl.exe bash -c \"" + sshCmd.replace("\"", "\\\"") + "\"";
    } else {
      fullCmd = sshCmd;
    }

    Process process = Runtime.getRuntime().exec(
        Commons.runningWindows()
            ? new String[]{"cmd", "/c", fullCmd}
            : new String[]{"bash", "-c", fullCmd});

    StringBuilder output = new StringBuilder();
    StringBuilder error = new StringBuilder();

    try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
         BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream(), "UTF-8"))) {
      String line;
      while ((line = out.readLine()) != null) output.append(line).append("\n");
      while ((line = err.readLine()) != null) error.append(line).append("\n");
    }

    boolean finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("SSH command timed out after 120 seconds");
    }

    if (output.length() == 0 && error.length() > 0) {
      throw new RuntimeException("SSH command error: " + error);
    }

    return output.toString().trim();
  }

  /**
   * Launch a command without waiting for output (fire-and-forget).
   */
  public static void execFireAndForget(String command) throws IOException {
    Runtime.getRuntime().exec(command);
  }

  /**
   * Launch a command array without waiting for output.
   */
  public static void execFireAndForget(String[] command) throws IOException {
    Runtime.getRuntime().exec(command);
  }

  /**
   * Check if a command/tool is available on the system (or in WSL on Windows).
   */
  public static boolean isToolAvailable(String toolName) {
    try {
      String whichCmd = "which " + toolName;
      Stream<String> result;
      if (Commons.runningWindows()) {
        result = execWsl(whichCmd);
      } else {
        result = execLocal(whichCmd);
      }
      boolean found = result.findFirst().isPresent();
      result.close();
      return found;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Check if a process is running by name.
   */
  public static boolean isProcessRunning(String processName) {
    try {
      String cmd;
      if (Commons.runningWindows()) {
        cmd = "tasklist";
      } else if (Commons.runningMac()) {
        cmd = "ps aux";
      } else {
        cmd = "ps aux";
      }
      try (Stream<String> lines = execLocal(cmd)) {
        return lines.anyMatch(l -> l.toLowerCase().contains(processName.toLowerCase()));
      }
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Install a package via apt (WSL on Windows, direct on Linux).
   * Returns true if install succeeded.
   */
  public static boolean aptInstall(String packageName) {
    try {
      String cmd = "sudo apt-get install -y " + packageName;
      if (Commons.runningWindows()) {
        execWsl(cmd).close();
      } else if (Commons.runningLinux()) {
        execLocal(cmd).close();
      } else {
        return false;
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Add a host to SSH known_hosts via ssh-keyscan.
   */
  public static boolean addSshFingerprint(String host) {
    try {
      String cmd = "ssh-keyscan -H " + host + " >> ~/.ssh/known_hosts 2>/dev/null";
      if (Commons.runningWindows()) {
        execWsl(cmd).close();
      } else {
        execLocal(cmd).close();
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Check if a host is in SSH known_hosts.
   */
  public static boolean isSshHostKnown(String host) {
    try {
      String cmd = "ssh-keygen -F " + host;
      Stream<String> result;
      if (Commons.runningWindows()) {
        result = execWsl(cmd);
      } else {
        result = execLocal(cmd);
      }
      boolean found = result.findFirst().isPresent();
      result.close();
      return found;
    } catch (Exception e) {
      return false;
    }
  }
}
