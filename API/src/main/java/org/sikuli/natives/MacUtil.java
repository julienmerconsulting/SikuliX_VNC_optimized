/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.natives;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.sikuli.natives.mac.jna.CoreGraphics;
import org.sikuli.natives.mac.jna.ObjC;
import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation;
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;

public class MacUtil extends GenericOsUtil {

  // NSApplicationActivationOptions — see Apple docs for NSRunningApplication.
  private static final int NS_ACTIVATE_ALL_WINDOWS          = 1 << 0;
  private static final int NS_ACTIVATE_IGNORING_OTHER_APPS  = 1 << 1;

  private static final Pointer NS_RUNNING_APP_CLASS = ObjC.cls("NSRunningApplication");
  private static final Pointer SEL_RUNNING_WITH_PID = ObjC.sel("runningApplicationWithProcessIdentifier:");
  private static final Pointer SEL_ACTIVATE_WITH   = ObjC.sel("activateWithOptions:");
  private static final Pointer SEL_IS_ACTIVE       = ObjC.sel("isActive");

  @Override
  public boolean isUserProcess(OsProcess process) {
    if (process == null) {
      return false;
    } else {
      if (process.getPid() > 0) {
        String name = process.getName();
        if (name.isEmpty()) {
          return false;
        }
        if (!name.startsWith("/Applications/")
            && !name.startsWith("/System/Applications/")
            && !name.startsWith("/Library/Java/JavaVirtualMachines/")) {
          return false;
        }
        return true;
      }
    }
    return false;
  }

  private static final class MacWindow implements OsWindow {
    private long number;
    private String title;
    private long pid;
    private Rectangle bounds;

    public MacWindow(long number, String title, long pid, Rectangle bounds) {
      this.number = number;
      this.title = title;
      this.pid = pid;
      this.bounds = bounds;
    }

    @Override
    public OsProcess getProcess() {
      Optional<ProcessHandle> handle = ProcessHandle.of(pid);
      if (handle.isPresent()) {
        return new GenericOsProcess(handle.get());
      }
      return null;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public Rectangle getBounds() {
      return bounds;
    }

    @Override
    public boolean focus() {
      Pointer app = getRunningApplication((int) pid);
      if (app == null) {
        return false;
      }
      return ObjC.msgSendBool(app, SEL_ACTIVATE_WITH,
          NS_ACTIVATE_ALL_WINDOWS | NS_ACTIVATE_IGNORING_OTHER_APPS);
    }

    @Override
    public boolean minimize() {
      throw new UnsupportedOperationException("minimize not implemented");
    }

    @Override
    public boolean maximize() {
      throw new UnsupportedOperationException("maximize not implemented");
    }

    @Override
    public boolean restore() {
      throw new UnsupportedOperationException("restore not implemented");
    }

    @Override
    public boolean equals(Object other) {
      return other != null && other instanceof MacWindow && this.number == (((MacWindow) other).number);
    }
  }

  @Override
  public List<OsWindow> findWindows(String title) {
    return allWindows().stream().filter((w) -> w.getTitle().contains(title)).collect(Collectors.toList());
  }

  @Override
  public OsWindow getFocusedWindow() {
    return allWindows().stream().filter((w) -> {
      OsProcess process = w.getProcess();

      if (process != null) {
        Pointer app = getRunningApplication((int) w.getProcess().getPid());
        if (app != null && ObjC.msgSendBool(app, SEL_IS_ACTIVE)) {
          return true;
        }
      }
      return false;
    }).findFirst().orElse(null);
  }

  @Override
  public List<OsWindow> getWindows(OsProcess process) {
    if (process != null) {
      return allWindows().stream().filter((w) -> process.equals(w.getProcess())).collect(Collectors.toList());
    }
    return new ArrayList<>(0);
  }

  @Override
  public List<OsWindow> getWindows() {
    return allWindows();
  }

  private static Pointer getRunningApplication(int pid) {
    try {
      Pointer app = ObjC.msgSend(NS_RUNNING_APP_CLASS, SEL_RUNNING_WITH_PID, pid);
      return (app == null || Pointer.nativeValue(app) == 0L) ? null : app;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static List<OsWindow> allWindows() {
    ArrayList<OsWindow> windows = new ArrayList<>();

    CFArrayRef windowInfo = CoreGraphics.INSTANCE.CGWindowListCopyWindowInfo(
        CoreGraphics.kCGWindowListExcludeDesktopElements | CoreGraphics.kCGWindowListOptionOnScreenOnly, 0);

    try {
      int numWindows = windowInfo.getCount();
      for (int i = 0; i < numWindows; i++) {
        Pointer pointer = windowInfo.getValueAtIndex(i);
        CFDictionaryRef windowRef = new CFDictionaryRef(pointer);

        Pointer numberPointer = windowRef.getValue(CoreGraphics.kCGWindowNumber);
        long windowNumber = new CFNumberRef(numberPointer).longValue();

        Pointer pidPointer = windowRef.getValue(CoreGraphics.kCGWindowOwnerPID);
        long windowPid = new CFNumberRef(pidPointer).longValue();

        String windowName = "";
        Pointer namePointer = windowRef.getValue(CoreGraphics.kCGWindowName);

        if (namePointer != null) {
          CFStringRef nameRef = new CFStringRef(namePointer);

          if (CoreFoundation.INSTANCE.CFStringGetLength(nameRef).intValue() > 0) {
            windowName = new CFStringRef(namePointer).stringValue();
          }
        }

        Pointer boundsPointer = windowRef.getValue(CoreGraphics.kCGWindowBounds);
        CoreGraphics.CGRectRef rect = new CoreGraphics.CGRectRef();

        boolean result = CoreGraphics.INSTANCE.CGRectMakeWithDictionaryRepresentation(boundsPointer, rect);

        Rectangle javaRect = null;

        if (result) {
          int x = (int) rect.origin.x;
          int y = (int) rect.origin.y;
          int width = (int) rect.size.width;
          int height = (int) rect.size.height;

          javaRect = new Rectangle(x, y, width, height);
        }

        windows.add(new MacWindow(windowNumber, windowName, windowPid, javaRect));
      }
    } finally {
      windowInfo.release();
    }

    return windows;
  }
}
