/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */

package org.sikuli.android;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.sikuli.basics.Debug;
import org.sikuli.support.Commons;
import org.sikuli.support.FileManager;
import org.sikuli.script.ScreenImage;
import se.vidstige.jadb.JadbDevice;
import se.vidstige.jadb.JadbException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADBDevice {

/*
  static {
    Commons.loadOpenCV();
  }
*/

  private static int lvl = 3;

  private static void log(int level, String message, Object... args) {
    Debug.logx(level, "ADBDevice: " + message, args);
  }

  private JadbDevice device = null;
  private int devW = -1;
  private int devH = -1;
  private ADBRobot robot = null;
  private ADBScreen screen = null;
  private boolean isMulti = false;

  private List<String> deviceProps = new ArrayList<>();
  private int deviceVersion = -1;
  private String sDeviceVersion = "???";


  private static ADBDevice adbDevice = null;
  private String adbExec = "";

  // Persistent adb shell used by high-frequency input commands. JADB creates a
  // fresh transport for every executeShell() call; keeping one adb shell alive
  // removes that per-action handshake while preserving command ordering.
  private Process inputShellProcess = null;
  private BufferedWriter inputShellWriter = null;
  private BufferedReader inputShellReader = null;
  private long inputShellSequence = 0;


  public static int KEY_HOME = 3;
  public static int KEY_BACK = 4;
  public static int KEY_MENU = 82;
  public static int KEY_POWER = 26;

  private ADBDevice() {
  }

  public static ADBDevice init() {
    return init("");
  }

  public static ADBDevice init(String adbExec) {
    if (adbDevice == null) {
      adbDevice = new ADBDevice();
      adbDevice.device = ADBClient.getDevice(adbExec);
      if (adbDevice.device == null) {
        adbDevice = null;
      } else {
        adbDevice.initDevice(adbDevice);
        adbDevice.adbExec = ADBClient.getADB();
        // OpenCV is loaded lazily on first screen-capture call (see
        // captureDeviceScreenMat). Skipping it here keeps init() lightweight
        // for use cases that only need input/keyevent/swipe — no image work.
      }
    }
    return adbDevice;
  }

  public static ADBDevice init(int id) {
      ADBDevice adbDevice = new ADBDevice();
      adbDevice.device = ADBClient.getDevice(id);
      if (adbDevice.device == null) {
        return null;
      } else {
        adbDevice.initDevice(adbDevice);
        adbDevice.adbExec = ADBClient.getADB();
        // OpenCV is loaded lazily on first screen-capture call — see comment
        // in init() above.
      }
    return adbDevice;
  }

  public static ADBDevice initBySerial(String serial) {
    ADBDevice adbDevice = new ADBDevice();
    adbDevice.device = ADBClient.getDeviceBySerial(serial);
    if (adbDevice.device == null) {
      return null;
    }
    adbDevice.initDevice(adbDevice);
    adbDevice.adbExec = ADBClient.getADB();
    return adbDevice;
  }

  private void initDevice(ADBDevice device) {
    device.deviceProps = Arrays.asList(device.exec("getprop").split("\n"));
    //[ro.build.version.release]: [6.0.1]
    //[ro.product.brand]: [google]
    //[ro.product.manufacturer]: [asus]
    //[ro.product.model]: [Nexus 7]
    //[ro.product.name]: [razor]
    //[ro.serialno]: [094da986]
    Pattern pProp = Pattern.compile("\\[(.*?)\\]:.*?\\[(.*)\\]");
    Matcher mProp = null;
    String val = "";
    String key = "";
    for (String prop : device.deviceProps) {
      if (!prop.startsWith("[ro.")) continue;
      mProp = pProp.matcher(prop);
      if (mProp.find()) {
        key = mProp.group(1);
        if (key.contains("build.version.release")) {
          val = mProp.group(2);
          try {
            device.deviceVersion = Integer.parseInt(val.split("\\.")[0]);
            device.sDeviceVersion = val;
          } catch (Exception e) {
          }
        }
      }
    }
    log(lvl, "init: %s", device.toString());
  }

  public static void reset() {
    if (adbDevice != null) {
      adbDevice.closeInputShell();
    }
    adbDevice = null;
    ADBClient.reset();
  }

  public String toString() {
    return String.format("attached device: serial(%s) display(%dx%d) version(%s)",
            getDeviceSerial(), getBounds().width, getBounds().height, sDeviceVersion);
  }

  public ADBRobot getRobot(ADBScreen screen) {
    if (robot == null) {
      this.screen = screen;
      robot = new ADBRobot(screen, this);
    }
    return robot;
  }

  public String getDeviceSerial() {
    return device.getSerial();
  }

  public Rectangle getBounds() {
    if (devW < 0) {
      Dimension dim = getDisplayDimension();
      devW = (int) dim.getWidth();
      devH = (int) dim.getHeight();
    }
    return new Rectangle(0, 0, devW, devH);
  }

  public ScreenImage captureScreen() {
    BufferedImage bimg = captureDeviceScreen();
    return new ScreenImage(getBounds(), bimg);
  }

  public ScreenImage captureScreen(Rectangle rect) {
    BufferedImage bimg = captureDeviceScreen(rect.x, rect.y, rect.width, rect.height);
    return new ScreenImage(rect, bimg);
  }

  /**
   * Lightweight PNG capture for visual polling.
   * Avoids the raw RGBA/OpenCV capture path used by normal OculiX matching.
   */
  public BufferedImage capturePngFast() {
    try (InputStream deviceOut = device.execute("screencap", "-p")) {
      BufferedImage image = ImageIO.read(deviceOut);
      if (image == null) {
        log(-1, "capturePngFast: PNG decoding returned null");
      }
      return image;
    } catch (Exception e) {
      log(-1, "capturePngFast: %s", e);
      return null;
    }
  }

  public BufferedImage captureDeviceScreen() {
    return captureDeviceScreen(0, 0, -1, -1);
  }

  public BufferedImage captureDeviceScreen(int x, int y, int w, int h) {
    Mat matImage = captureDeviceScreenMat(x, y, w, h);
    BufferedImage bImage = null;
    if (matImage != null) {
      bImage = new BufferedImage(matImage.width(), matImage.height(), BufferedImage.TYPE_3BYTE_BGR);
      byte[] bImageData = ((DataBufferByte) bImage.getRaster().getDataBuffer()).getData();
      matImage.get(0, 0, bImageData);
    }
    return bImage;
  }

  public Mat captureDeviceScreenMat(int x, int y, int actW, int actH) {
    // Lazy-load OpenCV — only the screen-capture path needs the native lib.
    // Idempotent: Commons.loadOpenCV() short-circuits if already loaded.
    Commons.loadOpenCV();
    log(lvl, "captureDeviceScreenMat: enter: [%d,%d %dx%d]", x, y, actW, actH);
    byte[] imagePrefix = new byte[12];
    byte[] image = new byte[0];
    boolean isfullScreen = false;
    if (x == 0 && y == 0 && actW < 0 && actH < 0) {
      isfullScreen = true;
    }
    int currentW;
    int currentH;
    int channels = 4;
    Mat matImage = new Mat();
    try (InputStream deviceOut = device.execute("screencap")) {
      Debug timer = Debug.startTimer();
      while (deviceOut.available() < 12) ;
      deviceOut.read(imagePrefix);
      if (imagePrefix[8] != 0x01) {
        log(-1, "captureDeviceScreenMat: image type not RGBA");
        return null;
      }
      currentW = byte2int(imagePrefix, 0, 4);
      currentH = byte2int(imagePrefix, 4, 4);
      if (!((currentW == devW && currentH == devH) || (currentH == devW && currentW == devH))) {
        log(-1, "captureDeviceScreenMat: width or height differ from device values");
        return null;
      }
      if (isfullScreen) {
        actW = currentW;
        actH = currentH;
      } else {
        if (x + actW > currentW) {
          actW = currentW - x;
        }
        if (y + actH > currentH) {
          actH = currentH - y;
        }
      }
      long duration = timer.lap("");
      int nPixels = actW * actH;
      image = new byte[nPixels * channels];
      int atImage = 0;
      boolean endOfStream = false;
      int maxR = y + actH;
      int maxC = x + actW;
      byte[] pixel = new byte[channels];
      int pixelByte = -1;
      while (true) {
        for (int npr = 0; npr < maxR; npr++) {
          for (int npc = 0; npc < currentW; npc++) {
            //byte[] pixel = deviceOut.readNBytes(4);
            for (int np = 0; np < channels; np++) {
              pixelByte = deviceOut.read();
              if (pixelByte > -1) {
                pixel[np] = (byte) pixelByte;
              } else {
                endOfStream = true;
              }
            }
	    if (!endOfStream) {
              if (npr >= y && npc >= x && npc < maxC) {
                image[atImage++] = pixel[0];
                image[atImage++] = pixel[1];
                image[atImage++] = pixel[2];
                image[atImage++] = pixel[3];
              }
            } else {
              break;
            }
          }
          if (endOfStream) {
            break;
          }
        }
        break;
      }
      Mat matOrg = new Mat(actH, actW, CvType.CV_8UC4);
      matOrg.put(0, 0, image);
      List<Mat> matsOrg = new ArrayList<Mat>();
      Core.split(matOrg, matsOrg);
      matsOrg.remove(3);
      List<Mat> matsImage = new ArrayList<Mat>();
      matsImage.add(matsOrg.get(2));
      matsImage.add(matsOrg.get(1));
      matsImage.add(matsOrg.get(0));
      Core.merge(matsImage, matImage);
      log(lvl, "captureDeviceScreenMat: exit: [%d,%d %dx%d] %d (%d)",
              x, y, actW, actH, duration, timer.end());
    } catch (Exception e) {
      log(-1, "captureDeviceScreenMat: [%d,%d %dx%d] %s", x, y, actW, actH, e);
    }
    return matImage;
  }

  private int byte2int(byte[] bytes, int start, int len) {
    int val = 0;
    int fact = 1;
    for (int i = start; i < start + len; i++) {
      int b = bytes[i] & 0xff;
      val += b * fact;
      fact *= 256;
    }
    return val;
  }

  private InputStream execADB(String... args) {
    if (args.length < 1) {
      return null;
    }
    ProcessBuilder app = new ProcessBuilder();
    List<String> cmd = new ArrayList<String>();
    Map<String, String> processEnv = app.environment();
    cmd.add(adbExec);
    for (String arg : args) {
      cmd.add(arg);
    }
    app.directory(null);
    app.redirectErrorStream(false);
    app.command(cmd);
    Process process = null;
    try {
      process = app.start();
      return process.getInputStream();
    } catch (IOException e) {
      log(-1, "execADB: %s (%s)", cmd, e);
    }
    return null;
  }

  /**
   * Execute an Android {@code input ...} command through one persistent
   * {@code adb -s <serial> shell} process. A unique marker is written after
   * every command and read back before returning, so callers are synchronized
   * with the end of the Android input command without Thread.sleep().
   */
  private synchronized boolean executePersistentInput(String... args) {
    StringBuilder command = new StringBuilder("input");
    for (String arg : args) {
      command.append(' ').append(shellQuote(arg));
    }

    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        ensureInputShell();

        String marker = "__OCULIX_INPUT_DONE_" + (++inputShellSequence) + "__:";
        inputShellWriter.write(command.toString());
        inputShellWriter.write("; echo " + marker + "$?");
        inputShellWriter.newLine();
        inputShellWriter.flush();

        String line;
        while ((line = inputShellReader.readLine()) != null) {
          if (line.startsWith(marker)) {
            if (!line.equals(marker + "0")) {
              log(-1, "persistent input command failed: %s -> %s", command, line);
            }
            return true;
          }
        }

        throw new IOException("persistent adb shell closed before completion marker");
      } catch (IOException e) {
        closeInputShell();
        if (attempt == 1) {
          log(-1, "persistent adb shell failed: %s", e);
        }
      }
    }

    return false;
  }

  private void inputCommand(String... args) {
    if (executePersistentInput(args)) {
      return;
    }

    // Safety fallback: JADB path, but consume stdout to EOF so this path is
    // still synchronous with command completion.
    exec("input", args);
  }

  private synchronized void ensureInputShell() throws IOException {
    if (inputShellProcess != null
        && inputShellProcess.isAlive()
        && inputShellWriter != null
        && inputShellReader != null) {
      return;
    }

    closeInputShell();

    if (adbExec == null || adbExec.isEmpty()) {
      throw new IOException("ADB executable is not configured");
    }

    List<String> cmd = new ArrayList<String>();
    cmd.add(adbExec);

    String serial = getDeviceSerial();
    if (serial != null && !serial.isEmpty()) {
      cmd.add("-s");
      cmd.add(serial);
    }

    cmd.add("shell");

    ProcessBuilder app = new ProcessBuilder(cmd);
    app.directory(null);
    app.redirectErrorStream(true);

    inputShellProcess = app.start();
    inputShellWriter = new BufferedWriter(
        new OutputStreamWriter(inputShellProcess.getOutputStream(), "UTF-8"));
    inputShellReader = new BufferedReader(
        new InputStreamReader(inputShellProcess.getInputStream(), "UTF-8"));

    log(lvl, "persistent adb shell started for %s", serial);
  }

  private synchronized void closeInputShell() {
    if (inputShellWriter != null) {
      try {
        inputShellWriter.write("exit");
        inputShellWriter.newLine();
        inputShellWriter.flush();
      } catch (IOException ignored) {
      }
    }

    if (inputShellProcess != null) {
      inputShellProcess.destroy();
    }

    try {
      if (inputShellWriter != null) {
        inputShellWriter.close();
      }
    } catch (IOException ignored) {
    }

    try {
      if (inputShellReader != null) {
        inputShellReader.close();
      }
    } catch (IOException ignored) {
    }

    inputShellProcess = null;
    inputShellWriter = null;
    inputShellReader = null;
  }

  private static String shellQuote(String value) {
    if (value == null) {
      return "''";
    }
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private Dimension getDisplayDimension() {
    // Primary: `wm size` — a small, stable "Physical size: WxH" contract that
    // holds across Android versions and on emulators (LDPlayer, BlueStacks, ...)
    // where the `dumpsys display` layout varies wildly and the
    // deviceWidth/deviceHeight keys are often absent (issue #229).
    Dimension dim = parseWmSize(exec("wm", "size"));
    if (dim != null) {
      return dim;
    }

    // Fallback only: the legacy `dumpsys display` parse, kept as a safety net
    // for exotic ROMs where `wm size` returns nothing. `dumpsys display` has no
    // stable output contract — which is exactly why it is no longer the primary.
    String dump = dumpsys("display");
    // Android < 12
    Pattern p1 = Pattern.compile("mDefaultViewport.*?deviceWidth=(\\d+).*?deviceHeight=(\\d+)");
    // Android 12+
    Pattern p2 = Pattern.compile("deviceWidth=(\\d+).*?deviceHeight=(\\d+)");
    Matcher match = p1.matcher(dump);
    if (!match.find()) {
      match = p2.matcher(dump);
    }
    if (match.find()) {
      return new Dimension(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    log(-1, "getDisplayDimension: could not detect device dimensions (wm size + dumpsys display both failed)");
    return null;
  }

  /**
   * Parse the output of {@code adb shell wm size}:
   * <pre>
   *   Physical size: 1080x2400
   *   Override size: 720x1600     (optional - wins when present)
   * </pre>
   *
   * @return the display dimension, or {@code null} if the output is empty or
   *         contains no parseable size line.
   */
  private Dimension parseWmSize(String wmOutput) {
    if (wmOutput == null || wmOutput.isEmpty()) {
      return null;
    }
    // An override (e.g. set via `wm size WxH`) reflects the actually rendered
    // surface, so it takes precedence over the physical panel size.
    Matcher override = Pattern.compile("Override size:\\s*(\\d+)x(\\d+)").matcher(wmOutput);
    if (override.find()) {
      return new Dimension(Integer.parseInt(override.group(1)), Integer.parseInt(override.group(2)));
    }
    Matcher physical = Pattern.compile("Physical size:\\s*(\\d+)x(\\d+)").matcher(wmOutput);
    if (physical.find()) {
      return new Dimension(Integer.parseInt(physical.group(1)), Integer.parseInt(physical.group(2)));
    }
    return null;
  }

  public String exec(String command, String... args) {
    String out = "";
    try (InputStream stdout = device.executeShell(command, args)) {
      out = inputStreamToString(stdout, "UTF-8");
    } catch (IOException | JadbException e) {
      log(-1, "exec: %s: %s", command, e);
      return null;
    }
    return out;
  }

  public String dumpsys(String component) {
    String out = "";
    if (component == null || component.isEmpty()) {
      component = "power";
    }
    try (InputStream stdout = component.toLowerCase().contains("all") ?
            device.executeShell("dumpsys") :
            device.executeShell("dumpsys", component)) {
      out = inputStreamToString(stdout, "UTF-8");
    } catch (IOException | JadbException e) {
      log(-1, "dumpsys: %s: %s", component, e);
    }
    return out;
  }

  public String printDump(String component) {
    String dump = dumpsys(component);
    if (!dump.isEmpty()) {
      System.out.println("***** Android device dump: " + component);
      System.out.println(dump);
    }
    return dump;
  }

  public String printDump() {
    String dump = dumpsys("all");
    if (!dump.isEmpty()) {
      File out = new File(Commons.getAppDataStore(), "android_dump_" + getDeviceSerial() + ".txt");
      System.out.println("***** Android device dump all services");
      System.out.println("written to file: " + out.getAbsolutePath());
      FileManager.writeStringToFile(dump, out);
    }
    return dump;
  }

  private static final int BUFFER_SIZE = 4 * 1024;

  private static String inputStreamToString(InputStream inputStream, String charsetName) {
    StringBuilder builder = new StringBuilder();
    InputStreamReader reader = null;
    try {
      reader = new InputStreamReader(inputStream, charsetName);
      char[] buffer = new char[BUFFER_SIZE];
      int length;
      while ((length = reader.read(buffer)) != -1) {
        builder.append(buffer, 0, length);
      }
      return builder.toString();
    } catch (Exception e) {
      return "";
    }
  }

  public void wakeUp(int seconds) {
    // KEYCODE_WAKEUP (224) is idempotent: it wakes the screen if asleep and is a
    // no-op if it is already on (unlike KEYCODE_POWER, it never sleeps). So there
    // is no need to read the screen state via dumpsys first — just send it.
    // `seconds` is kept for API compatibility; the old dumpsys-polling loop is
    // gone now that the wake is deterministic.
    inputKeyEvent(224);
    Commons.pause(0.5f);
  }

  public Boolean isDisplayOn() {
    // deviceidle | grep mScreenOn=true|false
    // v < 5: power | grep mScreenOn=true|false
    // v > 4: power | grep Display Power: state=ON|OFF
    String dump = dumpsys("power");
    Pattern displayOn = Pattern.compile("mScreenOn=(..)");
    String isOn = "tr";
    if (deviceVersion > 4) {
      displayOn = Pattern.compile("Display Power: state=(..)");
      isOn = "ON";
    }
    Matcher match = displayOn.matcher(dump);
    if (match.find()) {
      if (match.group(1).contains(isOn)) {
        return true;
      }
      return false;
    } else {
      log(-1, "isDisplayOn: (Android version %d) dumpsys power: pattern not found: %s", deviceVersion, displayOn);
    }
    return null;
  }

  public void inputKeyEvent(int key) {
    inputCommand("keyevent", Integer.toString(key));
  }

  public void tap(int x, int y) {
    inputCommand("tap", Integer.toString(x), Integer.toString(y));
  }

  public void swipe(int x1, int y1, int x2, int y2) {
    swipe(x1, y1, x2, y2, 0);
  }

  /**
   * Swipe gesture from (x1, y1) to (x2, y2) with optional duration.
   *
   * @param durationMs swipe duration in milliseconds; pass 0 to use the Android
   *                   default (~300 ms). Typical UI swipes use 50-300 ms.
   *                   Values above ~500 ms are interpreted as a drag by most
   *                   apps and may be intercepted — use {@link #dragAndDrop}
   *                   for intentional drag gestures.
   */
  public void swipe(int x1, int y1, int x2, int y2, int durationMs) {
    if (durationMs > 0) {
      inputCommand("swipe",
              Integer.toString(x1), Integer.toString(y1),
              Integer.toString(x2), Integer.toString(y2),
              Integer.toString(durationMs));
    } else {
      inputCommand("swipe",
              Integer.toString(x1), Integer.toString(y1),
              Integer.toString(x2), Integer.toString(y2));
    }
  }

  /**
   * Drag-and-drop gesture from (x1, y1) to (x2, y2) — touch + hold +
   * move + release. The initial press-hold distinguishes this from a
   * regular swipe and is required to move draggable UI elements
   * (home-screen icons, list reordering, file managers, etc.).
   * <p>
   * Requires Android 10+ (API 29). On older versions Android will
   * return an error and the gesture will be logged as failed.
   */
  public void dragAndDrop(int x1, int y1, int x2, int y2) {
    dragAndDrop(x1, y1, x2, y2, 0);
  }

  /**
   * Drag-and-drop gesture with optional duration.
   *
   * @param durationMs drag duration in milliseconds; pass 0 to use the
   *                   Android default. Typical drags use 500-1500 ms.
   */
  public void dragAndDrop(int x1, int y1, int x2, int y2, int durationMs) {
    if (durationMs > 0) {
      inputCommand("draganddrop",
              Integer.toString(x1), Integer.toString(y1),
              Integer.toString(x2), Integer.toString(y2),
              Integer.toString(durationMs));
    } else {
      inputCommand("draganddrop",
              Integer.toString(x1), Integer.toString(y1),
              Integer.toString(x2), Integer.toString(y2));
    }
  }

  private String textBuffer = "";
  private boolean typing = false;

  public synchronized boolean typeStarts() {
    if (!typing) {
      textBuffer = "";
      typing = true;
      return true;
    }
    return false;
  }

  public synchronized void typeEnds() {
    if (typing) {
      input(textBuffer);
      typing = false;
    }
  }

  public void typeChar(char character) {
    if (typing) {
      textBuffer += character;
    }
  }

  public static float inputDelay = 0.05f;

  public void input(String text) {
    try {
      device.executeShell("input text ", text);
      Commons.pause(text.length() * inputDelay);
    } catch (Exception e) {
      log(-1, "input: %s", e);
    }
  }
}
