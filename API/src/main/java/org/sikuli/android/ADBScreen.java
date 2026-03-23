/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.android;

import org.sikuli.basics.Debug;
import org.sikuli.script.*;
import org.sikuli.support.devices.IRobot;
import org.sikuli.support.devices.IScreen;
import org.sikuli.util.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Törcsi on 2016. 06. 26.
 * Revised by RaiMan
 */

//TODO possible to use https://github.com/Genymobile/scrcpy?

public class ADBScreen extends Region implements EventObserver, IScreen {

  private static boolean isFake = false;
  protected IRobot robot = null;
  private static int logLvl = 3;
  private ScreenImage lastScreenImage = null;
  private Rectangle bounds;

  private String promptMsg = "Select a region on the screen";
  private static int waitForScreenshot = 300;

  public boolean needsUnLock = false;
  public int waitAfterAction = 1;

  @Override
  public void waitAfterAction() {
    if (waitAfterAction > 0) {
      try {
        Thread.sleep(waitAfterAction);
      } catch (InterruptedException e) {
      }
    }
  }

  //---------------------------Inits
  private ADBDevice device = null;
  private static ADBScreen screen = null;

  public static ADBScreen start() {
    return start("");
  }

  public static ADBScreen start(String adbExec) {
    if (screen == null) {
      try {
        screen = new ADBScreen(adbExec);
      } catch (Exception e) {
        Debug.log(-1, "ADBScreen: start: No devices attached");
        screen = null;
      }
    }
    return screen;
  }

  public static void stop() {
    if (null != screen) {
      Debug.log(3, "ADBScreen: stopping android support");
      ADBDevice.reset();
      screen = null;
    }
  }

  public ADBScreen() {
    this("");
  }

  public ADBScreen(String adbExec) {
    super();
    setOtherScreen(this);
    device = ADBDevice.init(adbExec);
    init();
  }

  public ADBScreen(int id) {
    super();
    setOtherScreen(this);
    device = ADBDevice.init(id);
    init();
  }

  private void init() {
    if (device != null) {
      robot = device.getRobot(this);
      robot.setAutoDelay(10);
      bounds = device.getBounds();
      w = bounds.width;
      h = bounds.height;
    }
  }

  public boolean isValid() {
    return null != device;
  }

  public ADBDevice getDevice() {
    return device;
  }

//  private ADBScreen getScreenWithDevice(int id) {
//    if (screen == null) {
//      log(-1, "getScreenWithDevice: Android support not started");
//      return null;
//    }
//    ADBScreen multiScreen = new ADBScreen(id);
//    if (!multiScreen.isValid()) {
//      log(-1, "getScreenWithDevice: no device with id = %d", id);
//      return null;
//    }
//    return multiScreen;
//  }

  public String toString() {
    if (null == device) {
      return "Android:INVALID";
    } else {
      return String.format("Android %s", getDeviceDescription());
    }
  }

  public String getDeviceDescription() {
    return String.format("%s (%d x %d)", device.getDeviceSerial(), bounds.width, bounds.height);
  }

  public void wakeUp(int seconds) {
    if (null == device) {
      return;
    }
    if (null == device.isDisplayOn()) {
      Debug.log(-1, "wakeUp: not possible - see log");
      return;
    }
    if (!device.isDisplayOn()) {
      device.wakeUp(seconds);
      if (needsUnLock) {
        aSwipeUp();
      }
    }
  }

  public String exec(String command, String... args) {
    if (device == null) {
      return null;
    }
    return device.exec(command, args);
  }

  //-----------------------------Overrides
  @Override
  public IScreen getScreen() {
    return this;
  }

  @Override
  public void update(EventSubject event) {
    OverlayCapturePrompt ocp = (OverlayCapturePrompt) event;
    if (!ocp.isCanceled()) {
      capturedImage = ocp.getSelectionImage();
      if (capturedImage != null) {
        capturedRectangle = ocp.getSelectionRectangle();
      }
    }
    ocp.close();
    userCaptureActive.set(false);
  }

  @Override
  public IRobot getRobot() {
    return robot;
  }

  @Override
  public Rectangle getBounds() {
    return bounds;
  }

  @Override
  public ScreenImage capture() {
    return capture(x, y, -1, -1);
  }

  @Override
  public ScreenImage capture(int x, int y, int w, int h) {
    ScreenImage simg = null;
    if (device != null) {
      Debug.log(3, "ADBScreen.capture: (%d,%d) %dx%d", x, y, w, h);
      simg = device.captureScreen(new Rectangle(x, y, w, h));
    } else {
      Debug.log(-1, "capture: no ADBRobot available");
    }
    lastScreenImage = simg;
    return simg;
  }

  @Override
  public ScreenImage capture(Region reg) {
    return capture(reg.x, reg.y, reg.w, reg.h);
  }

  @Override
  public ScreenImage capture(Rectangle rect) {
    return capture(rect.x, rect.y, rect.width, rect.height);
  }

  @Override
  public int getID() {
    return 0;
  }

  public String getIDString() {
    return "Android " + getDeviceDescription();
  }

  @Override
  public ScreenImage getLastScreenImageFromScreen() {
    return lastScreenImage;
  }

  private EventObserver captureObserver = null;
  private AtomicBoolean userCaptureActive = new AtomicBoolean(false);
  private BufferedImage capturedImage = null;
  private Rectangle capturedRectangle = null;

  @Override
  public ScreenImage userCapture(final String msg) {
    if (robot == null) {
      return null;
    }
    userCaptureActive.set(true);
    capturedImage = null;
    Thread th = new Thread() {
      @Override
      public void run() {
        String message = msg.isEmpty() ? promptMsg : msg;
        userCaptureActive.set(OverlayCapturePrompt.capturePrompt(ADBScreen.this, message));
      }
    };

    th.start();

    while (userCaptureActive.get()) {
      this.wait(0.5f);
    }
    if (capturedImage != null) {
      lastScreenImage = new ScreenImage(capturedRectangle, capturedImage);
      return lastScreenImage;
    }
    return null;
  }

  public int getIdFromPoint(int srcx, int srcy) {
    return 0;
  }

  public Region set(Region element) {
    return setOther(element);
  }

  public Location set(Location element) {
    return setOther(element);
  }

  @Override
  public Region setOther(Region element) {
    return element.setOtherScreen(this);
  }

  @Override
  public Location setOther(Location element) {
    return element.setOtherScreen(this);
  }

  @Override
  public Region newRegion(Location loc, int width, int height) {
    return new Region(loc.x, loc.y, width, height, this);
  }

  @Override
  public Region newRegion(Region reg) {
    return new Region(reg).setOtherScreen(this);
  }

  @Override
  public Region newRegion(int _x, int _y, int width, int height) {
    return new Region(_x, _y, width, height, this);
  }

  @Override
  public Location newLocation(int _x, int _y) {
    return new Location(_x, _y).setOtherScreen(this);
  }

  @Override
  public Location newLocation(Location loc) {
    return new Location(loc).setOtherScreen(this);
  }

  @Override
  public Object action(String action, Object... args) {
    return null;
  }
}
