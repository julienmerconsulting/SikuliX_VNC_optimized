/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix.org - MIT license
 */
package org.sikuli.natives.mac.jna;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/**
 * Minimal JNA binding to the macOS Objective-C runtime (libobjc.A.dylib).
 * <p>
 * Replaces the abandoned {@code org.rococoa:rococoa-core:0.5} native dependency
 * (no arm64 slice, last updated circa 2011) by calling
 * {@code objc_getClass} / {@code sel_registerName} / {@code objc_msgSend}
 * directly. libobjc ships with the OS and covers every architecture Apple
 * currently supports.
 * <p>
 * Scope is deliberately tiny: OculiX only needs to invoke two methods of
 * {@code NSRunningApplication}. No attempt is made here to model the full
 * Objective-C type system.
 */
public final class ObjC {
  private ObjC() {}

  private static final NativeLibrary LIB = NativeLibrary.getInstance("objc.A");
  private static final Function OBJC_GET_CLASS = LIB.getFunction("objc_getClass");
  private static final Function SEL_REGISTER_NAME = LIB.getFunction("sel_registerName");
  private static final Function OBJC_MSG_SEND = LIB.getFunction("objc_msgSend");

  /** Returns the Objective-C class pointer for a class name, or {@code null} if unknown. */
  public static Pointer cls(String name) {
    return (Pointer) OBJC_GET_CLASS.invoke(Pointer.class, new Object[]{name});
  }

  /** Returns the selector (SEL) for a method name, registering it if needed. */
  public static Pointer sel(String name) {
    return (Pointer) SEL_REGISTER_NAME.invoke(Pointer.class, new Object[]{name});
  }

  /** Sends a message that returns an {@code id} (object pointer). */
  public static Pointer msgSend(Pointer receiver, Pointer selector, Object... args) {
    return (Pointer) OBJC_MSG_SEND.invoke(Pointer.class, prepend(receiver, selector, args));
  }

  /** Sends a message that returns a {@code BOOL}. */
  public static boolean msgSendBool(Pointer receiver, Pointer selector, Object... args) {
    Boolean r = (Boolean) OBJC_MSG_SEND.invoke(Boolean.class, prepend(receiver, selector, args));
    return Boolean.TRUE.equals(r);
  }

  private static Object[] prepend(Pointer receiver, Pointer selector, Object[] args) {
    Object[] all = new Object[2 + args.length];
    all[0] = receiver;
    all[1] = selector;
    System.arraycopy(args, 0, all, 2, args.length);
    return all;
  }
}
