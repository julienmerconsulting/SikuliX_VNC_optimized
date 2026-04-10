/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.ide.ui.recorder;

import raven.modal.Toast;

import java.awt.*;

/**
 * Thin wrapper around DJ-Raven toast notifications for the Recorder.
 * Phase 1: skeleton with 4 methods.
 */
public class RecorderNotifications {

  private static Component owner;

  public static void init(Component parentComponent) {
    owner = parentComponent;
  }

  public static void info(String message) {
    Toast.show(owner, Toast.Type.INFO, message);
  }

  public static void warning(String message) {
    Toast.show(owner, Toast.Type.WARNING, message);
  }

  public static void error(String message) {
    Toast.show(owner, Toast.Type.ERROR, message);
  }

  public static void success(String message) {
    Toast.show(owner, Toast.Type.SUCCESS, message);
  }
}
