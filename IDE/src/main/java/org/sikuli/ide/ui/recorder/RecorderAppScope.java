/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import net.miginfocom.swing.MigLayout;
import org.sikuli.script.App;
import org.sikuli.support.AppLauncher;
import org.sikuli.support.RemotePreflightCheck;

import javax.swing.*;
import java.util.List;

class RecorderAppScope {

  private App currentApp = null;
  private String appVarName = null;
  private boolean firstActionDone = false;
  private boolean remoteMode = false;

  private final JButton btnLaunchApp;
  private final JButton btnCloseApp;
  private final JCheckBox chkScopeToApp;
  private final RecorderCodeGen codeGen;

  RecorderAppScope(JButton btnLaunchApp, JButton btnCloseApp, JCheckBox chkScopeToApp, RecorderCodeGen codeGen) {
    this.btnLaunchApp = btnLaunchApp;
    this.btnCloseApp = btnCloseApp;
    this.chkScopeToApp = chkScopeToApp;
    this.codeGen = codeGen;
  }

  boolean isAppScoped() {
    return currentApp != null && chkScopeToApp.isSelected();
  }

  String getAppVarName() {
    return appVarName;
  }

  App getCurrentApp() {
    return currentApp;
  }

  boolean warnIfNoApp(JDialog parent) {
    if (!firstActionDone && currentApp == null) {
      int answer = JOptionPane.showConfirmDialog(parent,
          "No application launched. Launch your app first?\n\n"
          + "Recording without Launch App will act on the full screen.",
          "Launch App first?",
          JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
      if (answer == JOptionPane.YES_OPTION) {
        handleLaunchApp(parent);
        return currentApp == null;
      }
    }
    firstActionDone = true;
    return false;
  }

  void handleLaunchApp(JDialog parent) {
    String[] modes = {"Local application", "Remote (VNC via SSH)"};
    int mode = JOptionPane.showOptionDialog(parent,
        "Choose launch mode:",
        "Launch App",
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
        null, modes, modes[0]);

    if (mode < 0) return;

    if (mode == 0) {
      launchLocal(parent);
    } else {
      launchRemoteVNC(parent);
    }
  }

  private void launchLocal(JDialog parent) {
    String appPath = JOptionPane.showInputDialog(parent,
        "Application path or command:", "Launch Local App", JOptionPane.PLAIN_MESSAGE);
    if (appPath == null || appPath.trim().isEmpty()) return;
    appPath = appPath.trim();

    try {
      currentApp = App.open(appPath);
      if (currentApp == null) {
        RecorderNotifications.error("Failed to launch: " + appPath);
        return;
      }

      remoteMode = false;
      onAppLaunched(appPath);
      codeGen.generateLaunchApp(appPath, appVarName, chkScopeToApp.isSelected());
      RecorderNotifications.success("Launched: " + appPath);
    } catch (Exception ex) {
      RecorderNotifications.error("Launch failed: " + ex.getMessage());
    }
  }

  private void launchRemoteVNC(JDialog parent) {
    JPanel panel = new JPanel(new MigLayout("wrap 2, insets 8", "[right][grow, fill]"));
    JTextField tfHost = new JTextField(20);
    JTextField tfUser = new JTextField(20);
    JPasswordField tfPass = new JPasswordField(20);
    JTextField tfPort = new JTextField("5900", 8);

    panel.add(new JLabel("Host / IP:"));
    panel.add(tfHost);
    panel.add(new JLabel("SSH User:"));
    panel.add(tfUser);
    panel.add(new JLabel("SSH Password:"));
    panel.add(tfPass);
    panel.add(new JLabel("VNC Port:"));
    panel.add(tfPort);

    int result = JOptionPane.showConfirmDialog(parent, panel,
        "Remote VNC Connection", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

    if (result != JOptionPane.OK_OPTION) return;

    String host = tfHost.getText().trim();
    String user = tfUser.getText().trim();
    String password = new String(tfPass.getPassword());
    String port = tfPort.getText().trim();

    if (host.isEmpty() || user.isEmpty()) {
      RecorderNotifications.error("Host and user are required.");
      return;
    }

    List<RemotePreflightCheck.CheckResult> checks = RemotePreflightCheck.runAll(host);

    boolean allPassed = RemotePreflightCheck.allPassed(checks);
    if (!allPassed) {
      StringBuilder msg = new StringBuilder("Pre-flight checks:\n\n");
      for (RemotePreflightCheck.CheckResult check : checks) {
        msg.append(check.passed ? "  ✓  " : "  ✗  ");
        msg.append(check.name).append(": ").append(check.message).append("\n");
      }
      msg.append("\nFix issues and retry, or continue anyway?");

      String[] options = {"Fix all", "Continue anyway", "Cancel"};
      int choice = JOptionPane.showOptionDialog(parent, msg.toString(),
          "Pre-flight Results",
          JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
          null, options, options[0]);

      if (choice == 0) {
        for (RemotePreflightCheck.CheckResult check : checks) {
          if (!check.passed && check.autoFix != null) {
            check.autoFix.run();
          }
        }
        RecorderNotifications.success("Auto-fix applied. Try launching again.");
        return;
      }
      if (choice == 2 || choice < 0) return;
    }

    try {
      AppLauncher.launchRemoteVNC(host, user, password, port);

      remoteMode = true;
      currentApp = new App("vncviewer");
      onAppLaunched("vnc_" + host);
      codeGen.generateLaunchApp(host, appVarName, false);
      RecorderNotifications.success("VNC connected to " + host);
    } catch (Exception ex) {
      RecorderNotifications.error("VNC launch failed: " + ex.getMessage());
    }
  }

  private void onAppLaunched(String appPath) {
    btnLaunchApp.setEnabled(false);
    btnCloseApp.setEnabled(true);
    chkScopeToApp.setEnabled(true);

    appVarName = appPath.replaceAll(".*[/\\\\]", "")
        .replaceAll("\\.[^.]+$", "")
        .replaceAll("[^a-zA-Z0-9]", "")
        .toLowerCase();
    if (appVarName.isEmpty()) appVarName = "app";
  }

  void handleCloseApp() {
    if (currentApp != null) {
      try {
        currentApp.close();
        codeGen.generateCloseApp(appVarName);
        RecorderNotifications.success("App closed");
      } catch (Exception ex) {
        RecorderNotifications.error("Close failed: " + ex.getMessage());
      }
    }
    currentApp = null;
    appVarName = null;
    remoteMode = false;
    btnLaunchApp.setEnabled(true);
    btnCloseApp.setEnabled(false);
    chkScopeToApp.setEnabled(false);
  }

  void focusAppIfScoped() {
    if (currentApp != null && chkScopeToApp.isSelected()) {
      try {
        currentApp.focus();
      } catch (Exception ignored) {
      }
    }
  }
}
