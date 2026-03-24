/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.idesupport;

import java.awt.Image;
import java.awt.Taskbar;

public class IDETaskbarSupport {

  /**
   * Sets the task icon in the OS task bar.
   *
   * @param img the task image to set.
   */
  public static void setTaksIcon(Image img) {
    if (Taskbar.isTaskbarSupported()) {
      Taskbar taskbar = Taskbar.getTaskbar();
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        taskbar.setIconImage(img);
      }
    }
  }
}
