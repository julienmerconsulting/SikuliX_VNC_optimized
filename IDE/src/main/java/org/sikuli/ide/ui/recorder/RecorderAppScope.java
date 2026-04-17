/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import org.sikuli.script.App;

import javax.swing.*;

class RecorderAppScope {

  private App currentApp = null;
  private String appVarName = null;
  private boolean firstActionDone = false;

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
    String appPath = JOptionPane.showInputDialog(parent,
        "Application path or command:", "Launch App", JOptionPane.PLAIN_MESSAGE);
    if (appPath == null || appPath.trim().isEmpty()) return;
    appPath = appPath.trim();

    try {
      currentApp = App.open(appPath);
      if (currentApp == null) {
        RecorderNotifications.error("Failed to launch: " + appPath);
        return;
      }

      btnLaunchApp.setEnabled(false);
      btnCloseApp.setEnabled(true);
      chkScopeToApp.setEnabled(true);

      appVarName = appPath.replaceAll(".*[/\\\\]", "")
          .replaceAll("\\.[^.]+$", "")
          .replaceAll("[^a-zA-Z0-9]", "")
          .toLowerCase();
      if (appVarName.isEmpty()) appVarName = "app";

      codeGen.generateLaunchApp(appPath, appVarName, chkScopeToApp.isSelected());
      RecorderNotifications.success("Launched: " + appPath);
    } catch (Exception ex) {
      RecorderNotifications.error("Launch failed: " + ex.getMessage());
    }
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
