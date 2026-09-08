/*
 * Copyright (c) 2010-2024, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import com.sikulix.ocr.OCREngine;
import org.sikuli.basics.Settings;
import org.sikuli.support.devices.IScreen;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sikuli.android.ADBScreen;

/**
 * Composite keywords for OculiX — higher-level operations built on SikuliX primitives.
 * <p>
 * Each instance is bound to a {@link Region} (local screen, VNC, ADB).
 * No static state — safe for concurrent sessions.
 * </p>
 */
public class OculixKeywords {

  private Region region;
  private final IScreen screen;
  private double timeout = 3.0;
  private OCREngine ocrEngine = null;
  private final Map<String, Match> highlightMap = new HashMap<>();

  /**
   * Create keywords bound to a specific region.
   *
   * @param region the region to operate on (Screen, VNCScreen, ADBScreen, or sub-region)
   */
  public OculixKeywords(Region region) {
    if (region == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    this.region = region;
    this.screen = region.getScreen();
  }

  // ── Accessors ─────────────────────────────────────────────────────────

  public Region getRegion() {
    return region;
  }

  public void setRegion(Region region) {
    if (region == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    this.region = region;
  }

  public double getTimeout() {
    return timeout;
  }

  public void setTimeout(double timeout) {
    this.timeout = timeout;
  }

  /**
   * Set an OCR engine for text operations (clickText, regionClickText).
   * When set, uses this engine instead of SikuliX built-in Tesseract.
   * Pass null to revert to Tesseract.
   *
   * @param engine an OCREngine (e.g. PaddleOCREngine, TesseractEngine)
   */
  public void setOcrEngine(OCREngine engine) {
    this.ocrEngine = engine;
  }

  /**
   * Get the current OCR engine (null = SikuliX built-in Tesseract).
   */
  public OCREngine getOcrEngine() {
    return ocrEngine;
  }

  // ── Metrics ───────────────────────────────────────────────────────────

  /**
   * Get the similarity score of an image on screen.
   * Returns 0.0 if the image is not found (no exception thrown).
   */
  public double getMatchScore(String locator) {
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = region.find(pattern);
      if (match != null) {
        return match.getScore();
      }
      return 0.0;
    } catch (FindFailed e) {
      return 0.0;
    }
  }

  /**
   * Count the number of occurrences of an image on screen.
   * Returns 0 if the image is not found (no exception thrown).
   */
  public int imageCount(String locator) {
    Pattern pattern = Pattern.fromLocator(locator);
    List<Match> matches = region.findAllList(pattern);
    return matches.size();
  }

  // ── Click In (area + target) ──────────────────────────────────────────

  public int[] clickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.SINGLE);
  }

  public int[] doubleClickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.DOUBLE);
  }

  public int[] rightClickIn(String areaLocator, String targetLocator) {
    return performClickIn(areaLocator, targetLocator, ClickType.RIGHT);
  }

  // ── Click Nth ─────────────────────────────────────────────────────────

  public int[] clickNth(String locator, int index) {
    return clickNth(locator, index, true);
  }

  public int[] clickNth(String locator, int index, boolean sortByColumn) {
    Pattern pattern = Pattern.fromLocator(locator);
    List<Match> matches = sortByColumn
        ? region.findAllByColumn(pattern)
        : region.findAllByRow(pattern);
    if (matches == null || matches.isEmpty()) {
      throw new ScreenOperationException(
          "No occurrences of '" + locator + "' found on screen");
    }
    if (index < 0 || index >= matches.size()) {
      throw new ScreenOperationException(
          "Index " + index + " out of bounds, " + matches.size()
          + " occurrences found for '" + locator + "'");
    }
    Match match = matches.get(index);
    match.click();
    return MatchUtils.regionFromMatch(match);
  }

// ── Android swipes ─────────────────────────────────────────────────────

private static final int DEFAULT_ANDROID_SWIPE_DURATION_MS = 15;
private int androidSwipeDurationMs = DEFAULT_ANDROID_SWIPE_DURATION_MS;

private ADBScreen getADBScreen() {
  if (!(screen instanceof ADBScreen)) {
    throw new ScreenOperationException(
        "Swipe is currently available only on ADBScreen"
    );
  }

  return (ADBScreen) screen;
}

public int getAndroidSwipeDurationMs() {
  return androidSwipeDurationMs;
}

public void setAndroidSwipeDurationMs(int durationMs) {
  if (durationMs < 1) {
    throw new IllegalArgumentException("Android swipe duration must be >= 1 ms");
  }
  androidSwipeDurationMs = durationMs;
}

// ── Public swipe keywords ───────────────────────────────────────────────

public void swipeUp() {
  swipeUp(1);
}

public void swipeDown() {
  swipeDown(1);
}

public void swipeLeft() {
  swipeLeft(1);
}

public void swipeRight() {
  swipeRight(1);
}

public void swipeUp(int count) {
  ADBScreen adb = getADBScreen();

  for (int i = 0; i < count; i++) {

    int x = region.getX() + region.getW() / 2;

    int fromY =
        region.getY() + (region.getH() * 4 / 5);

    int toY =
        region.getY() + (region.getH() / 5);

    adb.getDevice().swipe(
        x,
        fromY,
        x,
        toY,
        androidSwipeDurationMs
    );
  }
}

public void swipeDown(int count) {
  ADBScreen adb = getADBScreen();

  for (int i = 0; i < count; i++) {

    int x = region.getX() + region.getW() / 2;

    int fromY =
        region.getY() + (region.getH() / 5);

    int toY =
        region.getY() + (region.getH() * 4 / 5);

    adb.getDevice().swipe(
        x,
        fromY,
        x,
        toY,
        androidSwipeDurationMs
    );
  }
}

public void swipeLeft(int count) {
  ADBScreen adb = getADBScreen();

  for (int i = 0; i < count; i++) {

    int y = region.getY() + region.getH() / 2;

    int fromX =
        region.getX() + (region.getW() * 4 / 5);

    int toX =
        region.getX() + (region.getW() / 5);

    adb.getDevice().swipe(
        fromX,
        y,
        toX,
        y,
        androidSwipeDurationMs
    );
  }
}

public void swipeRight(int count) {
  ADBScreen adb = getADBScreen();

  for (int i = 0; i < count; i++) {

    int y = region.getY() + region.getH() / 2;

    int fromX =
        region.getX() + (region.getW() / 5);

    int toX =
        region.getX() + (region.getW() * 4 / 5);

    adb.getDevice().swipe(
        fromX,
        y,
        toX,
        y,
        androidSwipeDurationMs
    );
  }
}

  // ── Click Text (OCR) ─────────────────────────────────────────────────

  public int[] clickText(String text) {
    return performClickText(text, region);
  }

  public int[] regionClickText(String text) {
    return performClickText(text, region);
  }

  // ── Click Region (by coordinates) ─────────────────────────────────────

  public void clickRegion(int x, int y, int w, int h) {
    clickRegion(x, y, w, h, 0, 0);
  }

  public void clickRegion(int x, int y, int w, int h, double waitChange) {
    clickRegion(x, y, w, h, waitChange, 0);
  }

  public void clickRegion(int x, int y, int w, int h, double waitChange, int highlightTimeout) {
    Region target = new Region(x, y, w, h);
    if (waitChange > 0) {
      Image imgBefore = target.getImage();
      target.click();
      target.waitVanish(imgBefore, waitChange);
    } else {
      target.click();
    }
    if (highlightTimeout > 0) {
      target.highlight(highlightTimeout);
    }
  }

  // ── Click On Match / Region (direct) ──────────────────────────────────

  public void clickOnMatch(Match match) {
    if (match == null) {
      throw new IllegalArgumentException("Match must not be null");
    }
    match.click();
  }

  public void clickOnRegion(Region target) {
    if (target == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    target.click();
  }

  public void doubleClickOnMatch(Match match) {
    if (match == null) {
      throw new IllegalArgumentException("Match must not be null");
    }
    try {
      match.doubleClick(match.getTarget());
    } catch (FindFailed e) {
      throw new ScreenOperationException("Double click on match failed", e);
    }
  }

  public void doubleClickOnRegion(Region target) {
    if (target == null) {
      throw new IllegalArgumentException("Region must not be null");
    }
    try {
      target.doubleClick(target.getCenter());
    } catch (FindFailed e) {
      throw new ScreenOperationException("Double click on region failed", e);
    }
  }

  // ── Composite Waits ───────────────────────────────────────────────────

  /**
   * Wait for a wanted image while watching for a not-wanted image.
   * If the not-wanted image appears first, throws immediately.
   *
   * @param wantedLocator    image to wait for
   * @param notWantedLocator image that should NOT appear
   * @param timeout          max wait in seconds
   * @return the Match of the wanted image
   */
  public Match waitForImage(String wantedLocator, String notWantedLocator, double timeout) {
    Pattern wantedPattern = Pattern.fromLocator(wantedLocator);
    Pattern notWantedPattern = Pattern.fromLocator(notWantedLocator);
    long pollMs = Math.round(1000.0 / Settings.WaitScanRate);
    long deadline = System.currentTimeMillis() + Math.round(timeout * 1000);

    while (System.currentTimeMillis() < deadline) {
      Match wanted = region.exists(wantedPattern, 0);
      if (wanted != null) {
        return wanted;
      }
      Match notWanted = region.exists(notWantedPattern, 0);
      if (notWanted != null) {
        throw new ScreenOperationException(
            "Not-wanted image '" + notWantedLocator + "' appeared: " + notWanted);
      }
      try {
        Thread.sleep(pollMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ScreenOperationException("Wait interrupted", e);
      }
    }
    throw new OculixTimeoutException(
        "Timeout after " + timeout + "s waiting for '" + wantedLocator + "'");
  }

  /**
   * Wait for any of the expected images while watching for not-expected images.
   *
   * @param timeout            max wait in seconds
   * @param pollingInterval    polling interval in seconds
   * @param expectedLocators   list of images to wait for (any one triggers success)
   * @param notExpectedLocators list of images that should NOT appear
   * @return the locator string of the first expected image found
   */
  public String waitForMultipleImages(double timeout, double pollingInterval,
      List<String> expectedLocators, List<String> notExpectedLocators) {
    if (expectedLocators == null || expectedLocators.isEmpty()) {
      throw new IllegalArgumentException("expectedLocators must not be null or empty");
    }
    long pollMs = Math.round(pollingInterval * 1000);
    long deadline = System.currentTimeMillis() + Math.round(timeout * 1000);

    while (System.currentTimeMillis() < deadline) {
      for (String locator : expectedLocators) {
        Match match = region.exists(Pattern.fromLocator(locator), 0);
        if (match != null) {
          return locator;
        }
      }
      if (notExpectedLocators != null) {
        for (String locator : notExpectedLocators) {
          Match match = region.exists(Pattern.fromLocator(locator), 0);
          if (match != null) {
            throw new ScreenOperationException(
                "Not-expected image '" + locator + "' appeared: " + match);
          }
        }
      }
      try {
        Thread.sleep(pollMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ScreenOperationException("Wait interrupted", e);
      }
    }
    throw new OculixTimeoutException(
        "Timeout after " + timeout + "s waiting for any of: " + expectedLocators);
  }

  // ── Extended Regions ──────────────────────────────────────────────────

  /**
   * Find an image and create an adjacent region (above/below only).
   *
   * @return [x, y, w, h] of the new region
   */
  public int[] getExtendedRegionFrom(String locator, String direction, int multiplier) {
    int[] base = waitAndGetMatchCoords(locator);
    return computeExtendedRegion(base[0], base[1], base[2], base[3], direction, multiplier);
  }

  /**
   * Find an image and create an adjacent region in any direction
   * (above, below, left, right, original).
   *
   * @return [x, y, w, h] of the new region
   */
  public int[] getExtendedRegionFromImage(String locator, String direction, int multiplier) {
    int[] base = waitAndGetMatchCoords(locator);
    return computeExtendedRegion(base[0], base[1], base[2], base[3], direction, multiplier);
  }

  /**
   * Create an adjacent region from an existing region's coordinates.
   *
   * @param regionCoords [x, y, w, h] of the source region
   * @return [x, y, w, h] of the new region
   */
  public int[] getExtendedRegionFromRegion(int[] regionCoords, String direction, int multiplier) {
    validateRegionCoords(regionCoords);
    return computeExtendedRegion(
        regionCoords[0], regionCoords[1], regionCoords[2], regionCoords[3],
        direction, multiplier);
  }

  /**
   * Move a region by "jumps" in a direction. Each jump = region size + margin.
   *
   * @param regionCoords [x, y, w, h] of the source region
   * @param direction    "above", "below", "left", "right"
   * @param jumps        number of jumps
   * @param margin       pixels of margin between each jump
   * @return [x, y, w, h] of the new region
   */
  public int[] fromRegionJumpTo(int[] regionCoords, String direction, int jumps, int margin) {
    validateRegionCoords(regionCoords);
    int x = regionCoords[0], y = regionCoords[1];
    int w = regionCoords[2], h = regionCoords[3];
    switch (direction.toLowerCase()) {
      case "below":
        y = y + (h + margin) * jumps;
        break;
      case "above":
        y = y - (h + margin) * jumps;
        break;
      case "right":
        x = x + (w + margin) * jumps;
        break;
      case "left":
        x = x - (w + margin) * jumps;
        break;
      default:
        throw new IllegalArgumentException("Invalid direction: " + direction
            + ". Use: above, below, left, right");
    }
    return new int[]{x, y, w, h};
  }

  /**
   * Find an image inside a specific region and return the match coordinates.
   *
   * @param regionCoords [x, y, w, h] to search within
   * @param locator      image locator
   * @return [x, y, w, h] of the match
   */
  public int[] returnMatchFromRegion(int[] regionCoords, String locator) {
    validateRegionCoords(regionCoords);
    Region searchRegion = new Region(
        regionCoords[0], regionCoords[1], regionCoords[2], regionCoords[3]);
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = searchRegion.find(pattern);
      return MatchUtils.regionFromMatch(match);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "'" + locator + "' not found in region " + java.util.Arrays.toString(regionCoords), e);
    }
  }

  // ── ROI Management ────────────────────────────────────────────────────

  /**
   * Set the working region (ROI).
   */
  public void setRoi(int x, int y, int w, int h) {
    this.region = new Region(x, y, w, h);
  }

  /**
   * Set the working region (ROI) with optional highlight.
   */
  public void setRoi(int x, int y, int w, int h, int highlightTimeout) {
    this.region = new Region(x, y, w, h);
    if (highlightTimeout > 0) {
      this.region.highlight(highlightTimeout);
    }
  }

  /**
   * Reset the working region to the full screen.
   * Screen-agnostic: uses the screen associated with the original region.
   */
  public void resetRoi() {
    this.region = new Region(screen.getBounds());
  }

  /**
   * Capture the current ROI and return the file path.
   */
  public String captureRoi() {
    return region.saveCapture();
  }

  /**
   * Capture the current ROI with a specific filename.
   */
  public String captureRoi(String imageName) {
    return region.saveCapture(imageName);
  }

  /**
   * Highlight the current ROI for the given duration.
   */
  public void highlightRoi(int timeout) {
    region.highlight(timeout);
  }

  // ── Persistent Highlights ─────────────────────────────────────────────

  /**
   * Highlight an image persistently (stays until clearHighlight is called).
   */
  public void highlight(String locator) {
    if (highlightMap.containsKey(locator)) {
      return; // already highlighted
    }
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = region.find(pattern);
      match.highlightOn();
      highlightMap.put(locator, match);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Cannot highlight '" + locator + "': not found", e);
    }
  }

  /**
   * Highlight an image for a given duration (auto-clears, not stored in map).
   */
  public void highlight(String locator, int secs) {
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = region.find(pattern);
      match.highlight(secs);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Cannot highlight '" + locator + "': not found", e);
    }
  }

  /**
   * Clear a persistent highlight for a specific image.
   */
  public void clearHighlight(String locator) {
    Match match = highlightMap.remove(locator);
    if (match != null) {
      match.highlightOff();
    }
  }

  /**
   * Clear all persistent highlights.
   */
  public void clearAllHighlights() {
    for (Match match : highlightMap.values()) {
      match.highlightOff();
    }
    highlightMap.clear();
  }

  /**
   * Get the number of active persistent highlights (for testing).
   */
  public int getHighlightCount() {
    return highlightMap.size();
  }

  // ── Captures ──────────────────────────────────────────────────────────

  /**
   * Capture a specific region by coordinates.
   */
  public String captureRegion(int x, int y, int w, int h) {
    return new Region(x, y, w, h).saveCapture();
  }

  /**
   * Capture a specific region with a given filename.
   */
  public String captureRegion(int x, int y, int w, int h, String imageName) {
    return new Region(x, y, w, h).saveCapture(imageName);
  }

  /**
   * Capture the full screen.
   */
  public String captureScreen() {
    return new Region(screen.getBounds()).saveCapture();
  }

  // ── Private helpers ───────────────────────────────────────────────────

  private enum ClickType { SINGLE, DOUBLE, RIGHT }

  private int[] performClickIn(String areaLocator, String targetLocator, ClickType clickType) {
    Pattern areaPattern = Pattern.fromLocator(areaLocator);
    Pattern targetPattern = Pattern.fromLocator(targetLocator);
    Match areaMatch;
    try {
      areaMatch = region.wait(areaPattern, timeout);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Area '" + areaLocator + "' not found on screen", e);
    }
    Match targetMatch;
    try {
      targetMatch = areaMatch.find(targetPattern);
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          "Target '" + targetLocator + "' not found inside area '" + areaLocator + "'", e);
    }
    try {
      switch (clickType) {
        case DOUBLE:
          areaMatch.doubleClick(targetPattern);
          break;
        case RIGHT:
          areaMatch.rightClick(targetPattern);
          break;
        default:
          areaMatch.click(targetPattern);
          break;
      }
    } catch (FindFailed e) {
      throw new ScreenOperationException(
          clickType + " click on '" + targetLocator + "' in area '" + areaLocator + "' failed", e);
    }
    return MatchUtils.regionFromMatch(targetMatch);
  }

  private int[] performClickText(String text, Region searchRegion) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Text must not be null or empty");
    }
    if (ocrEngine != null) {
      return performClickTextWithEngine(text, searchRegion);
    }
    // Fallback: SikuliX built-in Tesseract
    Match match = searchRegion.existsText(text, timeout);
    if (match == null) {
      throw new ScreenOperationException("Text '" + text + "' not found on screen");
    }
    match.click();
    return MatchUtils.regionFromMatch(match);
  }

  private int[] performClickTextWithEngine(String text, Region searchRegion) {
    // Capture the search region to a temp image for the OCR engine
    ScreenImage simg = searchRegion.getScreen().capture(searchRegion);
    String imagePath = simg.save();
    String json = ocrEngine.recognize(imagePath);
    int[] coords = ocrEngine.findTextCoordinates(json, text);
    if (coords == null) {
      throw new ScreenOperationException(
          "Text '" + text + "' not found by " + ocrEngine.getName());
    }
    // coords are relative to the captured image — offset to screen coordinates
    int absX = searchRegion.getX() + coords[0];
    int absY = searchRegion.getY() + coords[1];
    int w = coords[2];
    int h = coords[3];
    // Click the center of the found text
    Region textRegion = searchRegion.getScreen().newRegion(absX, absY, w, h);
    textRegion.click();
    return new int[]{absX, absY, w, h};
  }

  private int[] waitAndGetMatchCoords(String locator) {
    Pattern pattern = Pattern.fromLocator(locator);
    try {
      Match match = region.wait(pattern, timeout);
      return MatchUtils.regionFromMatch(match);
    } catch (FindFailed e) {
      throw new ScreenOperationException("'" + locator + "' not found on screen", e);
    }
  }

  static int[] computeExtendedRegion(int x, int y, int w, int h, String direction, int multiplier) {
    switch (direction.toLowerCase()) {
      case "below":
        return new int[]{x, y + h, w, h * multiplier};
      case "above":
        return new int[]{x, y - h * multiplier, w, h * multiplier};
      case "left":
        return new int[]{x - w * multiplier, y, w * multiplier, h};
      case "right":
        return new int[]{x + w, y, w * multiplier, h};
      case "original":
        return new int[]{x, y, w, h};
      default:
        throw new IllegalArgumentException("Invalid direction: " + direction
            + ". Use: above, below, left, right, original");
    }
  }

  private static void validateRegionCoords(int[] coords) {
    if (coords == null || coords.length != 4) {
      throw new IllegalArgumentException(
          "Region coordinates must be an int[4] array [x, y, w, h]");
    }
  }
}
