/*
 * Copyright (c) 2017 - sikulix.com - MIT license
 */
package com.sikulix.vnc;

import com.tigervnc.network.TcpSocket;
import com.tigervnc.rdr.FdInStreamBlockCallback;
import com.tigervnc.rfb.*;
import com.tigervnc.rfb.Exception;
import com.tigervnc.vncviewer.CConn;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;

import static com.sikulix.vnc.VNCClipboard.*;

public class VNCClient extends CConnection implements FdInStreamBlockCallback, Closeable {

  static ThreadLocal<UserPasswdGetter> UPG = new ThreadLocal<>();

  static {
    CConn.upg = new UserPasswdGetter() {
      @Override
      public boolean getUserPasswd(StringBuffer stringBuffer, StringBuffer stringBuffer1) {
        UserPasswdGetter upg = UPG.get();
        return false;
      }
    };
  }

  private final TcpSocket sock;
  private boolean shuttingDown = false;
  private PixelFormat serverPF;
  private int currentEncoding;
  private VNCFrameBuffer buffer;
  static LogWriter vlog = new LogWriter("VNCClient");

  public static void onDebugging() {
    LogWriter.setLogParams("300");
  }

  public static void offDebugging() {
    LogWriter.setLogParams("30");
  }

  public static VNCClient connect(String address, int port, String password, boolean shareConnection) {
    VNCClient client = new VNCClient(address, port, password, shareConnection);
    while (client.state() != VNCClient.RFBSTATE_NORMAL) {
      client.processMsg();
    }
    vlog.debug("running");
    return client;
  }

  private VNCClient(String address, int port, final String password, boolean shareConnection) {
    this.security = new ThreadLocalSecurityClient(new BasicUserPasswdGetter(password));

    this.currentEncoding = Encodings.encodingTight;
    this.setShared(shareConnection);

    setServerName(address);
    setServerPort(port);
    this.sock = new TcpSocket(this.getServerName(), this.getServerPort());
    this.sock.inStream().setBlockCallback(this);
    this.setStreams(this.sock.inStream(), this.sock.outStream());
    this.initialiseProtocol();
  }

  PixelFormat pixelFormat = null;
  int bufferW = 0;
  int bufferH = 0;

  @Override
  public void serverInit() {
    super.serverInit();
    this.serverPF = this.cp.pf();
    pixelFormat = serverPF;
    bufferW = this.cp.width;
    bufferH = this.cp.height;
    this.buffer = new VNCFrameBuffer(bufferW, bufferH, pixelFormat);
    this.writer().writeSetEncodings(this.currentEncoding, true);
  }

  public void serverCutText(String content, int length) {
    if (length > 0) {
      VNCClipboard.putTextToClipboard(PLAIN, UTF8, CHAR_BUFFER, content);
    }
  }

  public void setDesktopSize(int var1, int var2) {
    super.setDesktopSize(var1, var2);
    this.resizeFrameBuffer();
  }

  @Override
  public void clientRedirect(int i, String s, String s1) {

  }

  public void setColourMapEntries(int offset, int nbColors, int[] rgb) {
//    buffer.setColourMapEntries(offset, nbColors, rgb);
  }

  @Override
  public void bell() {

  }

  @Override
  public PixelFormat getPreferredPF() {
    return null;
  }

  private void resizeFrameBuffer() {
    if (this.buffer != null) {
      if (this.cp.width != 0 || this.cp.height != 0) {
        if (this.buffer.width() != this.cp.width || this.buffer.height() != this.cp.height) {
          this.buffer.resize(cp.width, cp.height);
        }
      }
    }
  }

  public void refreshFramebuffer() {
    refreshFramebuffer(0, 0, cp.width, cp.height, false);
  }

  /**
   * Sends FramebufferUpdateRequest message to server.
   *
   * @param x           X coordinate of desired region
   * @param y           Y coordinate of desired region
   * @param w           Width of desired region
   * @param h           Height of desired region
   * @param incremental Zero sends entire desktop, One sends changes only.
   */
  public void refreshFramebuffer(int x, int y, int w, int h, boolean incremental) {
    writer().writeFramebufferUpdateRequest(new Rect(x, y, w, h), incremental);
  }

  @Override
  public void framebufferUpdateStart() {
    refreshFramebuffer(0, 0, cp.width, cp.height, true);
  }

  @Override
  public void framebufferUpdateEnd() {
  }

  public void fillRect(Rect r, int p) {
    this.buffer.fillRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
  }

  public void imageRect(Rect r, Object p) {
    this.buffer.imageRect(r.tl.x, r.tl.y, r.width(), r.height(), p);
  }

  public void copyRect(Rect r, int sx, int sy) {
    this.buffer.copyRect(r.tl.x, r.tl.y, r.width(), r.height(), sx, sy);
  }

  /**
   * Tells VNC server to depress key.
   *
   * @param key X Window System Keysym for key.
   * @throws IOException If there is a socket error.
   */
  public void keyDown(int key) throws IOException {
    writer().writeKeyEvent(key, true);
  }

  /**
   * Tells VNC server to release key.
   *
   * @param key X Window System Keysym for key.
   * @throws IOException If there is a socket error.
   */
  public void keyUp(int key) throws IOException {
    writer().writeKeyEvent(key, false);
  }

  /**
   * Tells VNC server to perform a mouse event. bOne through bEight are mouse
   * buttons one through eight respectively.  A zero means release that
   * button, and a one means depress that button.
   *
   * @param buttonState logical or of BUTTON_N_DOWN
   * @param x           X coordinate of action
   * @param y           Y coordinate of action
   * @throws IOException If there is a socket error.
   */
  public void mouseEvent(int buttonState, int x, int y) throws IOException {
    writer().writePointerEvent(new Point(x, y), buttonState);
  }

  /**
   * Closes the connection
   *
   */
  public void close() {
    this.shuttingDown = true;
    if (this.sock != null) {
      this.sock.shutdown();
    }
    vlog.debug("shutting down");
  }

  public boolean isShuttingDown() {
    return  shuttingDown;
  }

  /**
   * Returns the VNCClient Object as a string
   */
  public String toString() {
    return "VNCClient: " + getServerName() + ":" + getServerPort();
  }

  public Rectangle getBounds() {
    return new Rectangle(0, 0, this.cp.width, this.cp.height);
  }

  public BufferedImage getFrameBuffer(int x, int y, int w, int h) {
    return buffer.getImage(x, y, w, h);
  }

  @Override
  public void blockCallback() {
    try {
      synchronized (this) {
        this.wait(1L);
      }
    } catch (InterruptedException var4) {
      throw new Exception(var4.getMessage());
    }
  }

  public void processMessages() {
    while (!shuttingDown) {
      processMsg();
    }
  }

}
