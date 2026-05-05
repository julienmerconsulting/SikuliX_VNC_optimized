/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix.org - MIT license
 */

package org.sikuli.idesupport;

import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.OpenFilesEvent;
import java.awt.desktop.OpenFilesHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.awt.desktop.QuitEvent;
import java.awt.desktop.QuitHandler;
import java.awt.desktop.QuitResponse;

import java.io.File;
import java.util.List;

import org.sikuli.basics.Debug;
import org.sikuli.ide.SikulixIDE;

/**
 * Native desktop support.
 */
public class IDEDesktopSupport implements AboutHandler, PreferencesHandler, QuitHandler, OpenFilesHandler {

  static SikulixIDE ide = null;

  private static String me = "IDE: ";
  private static int lvl = 3;

  private static void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

  public static List<File> macOpenFiles = null;
  public static boolean showAbout = true;
  public static boolean showPrefs = true;
  public static boolean showQuit = true;

  public static void init(SikulixIDE theIDE) {
    ide = theIDE;

    IDEDesktopSupport support = new IDEDesktopSupport();

    if(Desktop.isDesktopSupported()) {
      Desktop desktop = Desktop.getDesktop();

      if(desktop.isSupported(Desktop.Action.APP_ABOUT)){
        desktop.setAboutHandler(support);
        showAbout = false;
      }

      if(desktop.isSupported(Desktop.Action.APP_PREFERENCES)){
        desktop.setPreferencesHandler(support);
        showPrefs = false;
      }

      if(desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)){
        desktop.setQuitHandler(support);;
        showQuit = false;
      }

      if(desktop.isSupported(Desktop.Action.APP_OPEN_FILE)){
        desktop.setOpenFileHandler(support);
      }
    }
  }

  @Override
  public void openFiles(OpenFilesEvent e) {
    log(lvl, "nativeSupport: should open files");
    macOpenFiles = e.getFiles();
    for (File f : macOpenFiles) {
      log(lvl, "nativeSupport: openFiles: %s", macOpenFiles);
    }
  }

  @Override
  public void handleAbout(AboutEvent e) {
    ide.showAbout();
  }

  @Override
  public void handlePreferences(PreferencesEvent e) {
    ide.showPreferencesWindow();
  }

  @Override
  public void handleQuitRequestWith(QuitEvent e, QuitResponse response) {
    if (!ide.quit()) {
      response.cancelQuit();
    } else {
      response.performQuit();
    }
  }

}
