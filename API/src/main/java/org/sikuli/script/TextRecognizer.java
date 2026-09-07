/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.oculix.octachorix.OcrEngineMode;
import org.oculix.octachorix.OctachorixFault;
import org.oculix.octachorix.PageLevel;
import org.oculix.octachorix.PageSegMode;
import org.oculix.octachorix.Reading;
import org.oculix.octachorix.Scribe;
import org.oculix.octachorix.TextElement;
import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.support.Commons;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Intended to be used only internally - still public for being backward compatible
 * <p></p>
 * <b>New projects should use class OCR</b>
 * <p></p>
 * Implementation of the OCR API on top of Octachorix (Tesseract C API, bound by
 * absolute path to the Legerix-provisioned natives). One Tesseract session per
 * thread, initialised once and reused until the options change.
 */
public class TextRecognizer {

  private TextRecognizer() {
  }

  private static int lvl = 3;

  private OCR.Options options;

  //<editor-fold desc="00 instance, reset">

  /**
   * New TextRecognizer instance using the global options.
   *
   * @return instance
   * @deprecated no longer needed at all
   */
  @Deprecated
  public static TextRecognizer start() {
    return TextRecognizer.get(OCR.globalOptions());
  }

  /**
   * INTERNAL
   *
   * @param options an Options set
   * @return a new TextRecognizer instance
   */
  protected static TextRecognizer get(OCR.Options options) {
    checkLib();

    initDefaultDataPath();

    if (options == null) {
      options = OCR.globalOptions();
    }
    options.validate();

    TextRecognizer textRecognizer = new TextRecognizer();
    textRecognizer.options = options;

    return textRecognizer;
  }

  private static boolean isValid = false;

  private static String getTesseractInstallCommand() {
    // Tesseract natives are bundled via Legerix on all supported platforms
    // (mac/linux x86_64 + aarch64, windows x86_64) and bound by absolute path
    // through Octachorix. There is no system fallback on purpose: a failure
    // here is a packaging/extraction problem, never a missing system binary.
    return "  Reinstall OculiX — Tesseract binaries are bundled via Legerix.\n"
        + "  If the problem persists, please open an issue.\n\n"
        + "  More info: https://github.com/oculix-org/Oculix/wiki/OCR-Setup\n\n"
        + "══════════════════════════════════════════════════════════════";
  }

  private static void checkLib() {
    if (isValid) {
      return;
    }
    if (Commons.isTesseractLoaded()) {
      isValid = true;
      Debug.log(lvl, "OCR: start: Octachorix %s using bundled Tesseract (Legerix): %s",
          Commons.getSXVersionOctachorix(), Commons.getTesseractLibraryPath());
      return;
    }
    String msg = "\n\n"
        + "══════════════════════════════════════════════════════════════\n"
        + " Tesseract OCR engine not available.\n"
        + "══════════════════════════════════════════════════════════════\n\n"
        + " Reason: " + Commons.getTesseractFailure() + "\n\n"
        + " Fix:\n" + getTesseractInstallCommand();
    Debug.error(msg);
    throw new SikuliXception("Tesseract OCR engine not available: " + Commons.getTesseractFailure());
  }

  /**
   * Live Tesseract sessions, per thread, one per effective option set. A
   * Scribe is not thread-safe (its native handle mutates on every read), so
   * the cache lives in a ThreadLocal; OculiX runs one screen-operations
   * pipeline per thread, this matches. A session is built the first time an
   * option set (language, data path, oem, psm, variables, configs) is seen
   * and reused for every later call with the same set: TessBaseAPIInit — and
   * the traineddata read — happen once per option set, not on every OCR call.
   * readWords/readWord/readLine/readChar each have their own PSM and thus
   * their own session; alternating between them costs nothing after the
   * first call of each. Bounded to a handful of sessions, least recently
   * used evicted and closed.
   */
  private static final int MAX_SESSIONS = 6;

  private static final ThreadLocal<Map<String, Scribe>> SESSIONS = ThreadLocal.withInitial(
      () -> new java.util.LinkedHashMap<String, Scribe>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Scribe> eldest) {
          if (size() > MAX_SESSIONS) {
            eldest.getValue().close();
            return true;
          }
          return false;
        }
      });

  private static String sessionKey(OCR.Options options) {
    return options.oem() + "|" + options.psm() + "|" + options.language() + "|" + options.dataPath()
        + "|" + options.variables() + "|" + options.configs();
  }

  private Scribe getScribe() {
    checkLib();
    Map<String, Scribe> sessions = SESSIONS.get();
    String key = sessionKey(options);
    Scribe cached = sessions.get(key);
    if (cached != null && !cached.isClosed()) {
      return cached;
    }
    if (cached != null) {
      sessions.remove(key);
    }
    try {
      Scribe.Builder builder = Scribe.builder()
          .tesseractLibrary(Paths.get(Commons.getTesseractLibraryPath()))
          .leptonicaLibrary(Paths.get(Commons.getLeptonicaLibraryPath()))
          .datapath(Paths.get(options.dataPath()))
          .language(options.language())
          .ocrEngineMode(toOcrEngineMode(options.oem()));
      // OCR.Options.resetPSM() sets -1: historical "do not touch the PSM" sentinel.
      if (options.psm() < 0) {
        builder.pageSegModeUnset();
      } else {
        builder.pageSegMode(toPageSegMode(options.psm()));
      }
      for (Map.Entry<String, String> entry : options.variables().entrySet()) {
        builder.variable(entry.getKey(), entry.getValue());
      }
      if (!options.configs().isEmpty()) {
        builder.configs(new ArrayList<>(options.configs()));
      }
      Scribe scribe = builder.build();
      sessions.put(key, scribe);
      Debug.log(lvl, "OCR: session %d/%d: Tesseract %s (lang=%s, oem=%d, psm=%d)",
          sessions.size(), MAX_SESSIONS, scribe.tesseractVersion(), options.language(), options.oem(), options.psm());
      return scribe;
    } catch (OctachorixFault | UnsatisfiedLinkError e) {
      // Defense-in-depth net for #107: the pre-flight above only knows that
      // Legerix delivered files; this catch fires when binding or
      // TessBaseAPIInit itself fails — broken DLL, arch mismatch on Apple
      // Silicon, unreadable tessdata, unknown language.
      String msg = "\n\n"
          + "══════════════════════════════════════════════════════════════\n"
          + " Tesseract native library failed to initialise (Octachorix).\n"
          + "══════════════════════════════════════════════════════════════\n\n"
          + " Original error: \n" + e.getMessage() + "\n"
          + " Try:\n  " + getTesseractInstallCommand();
      Debug.error(msg);
      throw new SikuliXception("Tesseract native library failed to initialise (Octachorix).");
    }
  }

  private static OcrEngineMode toOcrEngineMode(int oem) {
    for (OcrEngineMode mode : OcrEngineMode.values()) {
      if (mode.value() == oem) {
        return mode;
      }
    }
    throw new IllegalArgumentException(String.format("OCR: Invalid OEM %s (0 .. 3)", oem));
  }

  private static PageSegMode toPageSegMode(int psm) {
    for (PageSegMode mode : PageSegMode.values()) {
      if (mode.value() == psm) {
        return mode;
      }
    }
    throw new IllegalArgumentException(String.format("OCR: Invalid PSM %s (0 .. 13)", psm));
  }

  private static PageLevel toPageLevel(int level) {
    for (PageLevel pageLevel : PageLevel.values()) {
      if (pageLevel.value() == level) {
        return pageLevel;
      }
    }
    throw new IllegalArgumentException(String.format("OCR: Invalid page iterator level %s (0 .. 4)", level));
  }

  /**
   * @see OCR#reset()
   * @deprecated use OCR.reset() instead
   */
  @Deprecated
  public static void reset() {
    OCR.globalOptions().reset();
  }

  /**
   * @see OCR#status()
   * @deprecated use OCR.status() instead
   */
  @Deprecated
  public static void status() {
    Debug.logp("Global settings " + OCR.globalOptions().toString());
  }
  //</editor-fold>

  //<editor-fold desc="02 set OEM, PSM">

  /**
   * @param oem
   * @return instance
   * @see OCR.Options#oem(OCR.OEM)
   * @deprecated Use options().oem()
   */
  @Deprecated
  public TextRecognizer setOEM(OCR.OEM oem) {
    return setOEM(oem.ordinal());
  }

  /**
   * @param oem
   * @return instance
   * @see OCR.Options#oem(int)
   * @deprecated use OCR.globalOptions().oem()
   */
  @Deprecated
  public TextRecognizer setOEM(int oem) {
    options.oem(oem);
    return this;
  }


  /**
   * @param psm
   * @return instance
   * @see OCR.Options#psm(OCR.PSM)
   * @deprecated use OCR.globalOptions().psm()
   */
  @Deprecated
  public TextRecognizer setPSM(OCR.PSM psm) {
    return setPSM(psm.ordinal());
  }

  /**
   * @param psm
   * @return instance
   * @see OCR.Options#psm(int)
   * @deprecated use OCR.globalOptions().psm()
   */
  @Deprecated
  public TextRecognizer setPSM(int psm) {
    options.psm(psm);
    return this;
  }
  //</editor-fold>

  //<editor-fold desc="03 set datapath, language, variable, configs">

  /**
   * @param dataPath
   * @return instance
   * @see OCR.Options#dataPath()
   * @deprecated use OCR.globalOptions().datapath()
   */
  @Deprecated
  public TextRecognizer setDataPath(String dataPath) {
    options.dataPath(dataPath);
    return this;
  }

  /**
   * @param language
   * @return instance
   * @see OCR.Options#language(String)
   * @deprecated use OCR.globalOptions().language()
   */
  @Deprecated
  public TextRecognizer setLanguage(String language) {
    options.language(language);
    return this;
  }

  /**
   * @param key
   * @param value
   * @return instance
   * @see OCR.Options#variable(String, String)
   * @deprecated use OCR.globalOptions().variable(String key, String value)
   */
  @Deprecated
  public TextRecognizer setVariable(String key, String value) {
    options.variable(key, value);
    return this;
  }

  /**
   * @param configs
   * @return instance
   * @see OCR.Options#configs(String...)
   * @deprecated Use OCR.globalOptions.configs(String... configs)
   */
  @Deprecated
  public TextRecognizer setConfigs(String... configs) {
    setConfigs(Arrays.asList(configs));
    return this;
  }

  /**
   * @param configs
   * @return
   * @see OCR.Options#configs(List)
   * @deprecated Use options.configs
   */
  @Deprecated
  public TextRecognizer setConfigs(List<String> configs) {
    options.configs(configs);
    return this;
  }
  //</editor-fold>

  //<editor-fold desc="10 image optimization">

  /**
   * @param size expected font size in pt
   * @see OCR.Options#fontSize(int)
   * @deprecated use OCR.globalOptions().fontSize(int size)
   */
  @Deprecated
  public TextRecognizer setFontSize(int size) {
    options.fontSize(size);
    return this;
  }

  /**
   * @param height of an uppercase X in px
   * @see OCR.Options#textHeight(float)
   * @deprecated use OCR.globalOptions().textHeight(int height)
   */
  @Deprecated
  public TextRecognizer setTextHeight(int height) {
    options.textHeight(height);
    return this;
  }

  private BufferedImage optimize(BufferedImage bimg) {
    Mat mimg = Commons.makeMat(bimg);

    Imgproc.cvtColor(mimg, mimg, Imgproc.COLOR_BGR2GRAY);

    // sharpen original image to primarily get rid of sub pixel rendering artifacts
    mimg = unsharpMask(mimg, 3);

    float rFactor = options.factor();

    // #378: on a large, full-screen search region the default factor (~3.0,
    // calibrated for tiny UI text) explodes the image to ~18 MP and drives
    // Tesseract LSTM to ~8s. Cap on > 1 MP regions; default 2.0 keeps precision
    // (-1 mot mesuré sur cleaned full-HD vs baseline) with ~×1.4 speedup.
    // Tunable via OCR.globalOptions().largeImageFactor(...) — Auchan-style
    // dashboards (text >= 14 px) can set 0.8 for ~×4 speedup.
    // Supersedes the original hardcoded 0.8 on this branch (commit 26d30979);
    // see #378 thread for the empirical justification.
    if ((long) mimg.cols() * mimg.rows() > 1_000_000L) {
      rFactor = options.largeImageFactor();
    }

    if (rFactor > 0 && rFactor != 1) {
      Commons.resize(mimg, rFactor, options.resizeInterpolation());
    }

    // sharpen the enlarged image again
    mimg = unsharpMask(mimg, 5);

    // invert if font color is said to be light
    if (options.isLightFont()) {
      Core.bitwise_not(mimg, mimg);
    }
    //TODO does it really make sense? invert in case of mainly dark background
//    else if (Core.mean(mimg).val[0] < 127) {
//      Core.bitwise_not(mimg, mimg);
//    }

    BufferedImage optImg = Commons.getBufferedImage(mimg);
    return optImg;
  }

  /*
   * sharpens the image using an unsharp mask
   */
  private Mat unsharpMask(Mat img, double sigma) {
    Mat blurred = new Mat();
    Imgproc.GaussianBlur(img, blurred, new Size(), sigma, sigma);
    Core.addWeighted(img, 1.5, blurred, -0.5, 0, img);
    return img;
  }
  //</editor-fold>

  //<editor-fold desc="20 text, lines, words - internal use">
  protected <SFIRBS> String readText(SFIRBS from) {
    return doRead(from);
  }

  protected <SFIRBS> List<Match> readLines(SFIRBS from) {
    BufferedImage bimg = Element.getBufferedImage(from);
    return readTextItems(bimg, OCR.PAGE_ITERATOR_LEVEL_LINE);
  }

  protected <SFIRBS> List<Match> readWords(SFIRBS from) {
    BufferedImage bimg = Element.getBufferedImage(from);
    return readTextItems(bimg, OCR.PAGE_ITERATOR_LEVEL_WORD);
  }
  //</editor-fold>

  //<editor-fold desc="30 helper">
  private static void initDefaultDataPath() {
    if (OCR.Options.defaultDataPath != null) {
      return;
    }
    // Priority order:
    //   1. Settings.OcrDataPath (user override)
    //   2. Legerix bundled tessdata (eng, fra, spa, chi_sim, hin)
    //   3. Legacy SikulixTesseract resource extraction
    String defaultDataPath = null;
    if (Settings.OcrDataPath != null) {
      defaultDataPath = new File(Settings.OcrDataPath, "tessdata").getAbsolutePath();
    } else {
      String legerixPath = Commons.getTesseractDataPath();
      if (legerixPath != null && new File(legerixPath).isDirectory()) {
        defaultDataPath = legerixPath;
      }
    }
    if (defaultDataPath == null) {
      File fTessDataPath = new File(Commons.getAppDataPath(), "SikulixTesseract/tessdata");
      boolean shouldExport = Commons.shouldExport();
      boolean fExists = fTessDataPath.exists();
      if (!fExists || shouldExport) {
        if (0 == Commons.extractResourcesToFolder("/tessdataSX", fTessDataPath, null).size()) {
          throw new SikuliXception(String.format("OCR: start: export tessdata did not work: %s", fTessDataPath));
        }
      }
      defaultDataPath = fTessDataPath.getAbsolutePath();
    }
    OCR.Options.defaultDataPath = defaultDataPath;
  }

  protected <SFIRBS> String doRead(SFIRBS from) {
    BufferedImage bimg = Element.getBufferedImage(from);
    try {
      // No page level requested: one Recognize pass, full text only, no iterator walk.
      Reading reading = getScribe().read(optimize(bimg), EnumSet.noneOf(PageLevel.class));
      return reading.text().trim().replace("\n\n", "\n");
    } catch (OctachorixFault e) {
      Debug.error("OCR: read: Octachorix: %s", e.getMessage());
      return "";
    }
  }

  protected <SFIRBS> List<Match> readTextItems(SFIRBS from, int level) {
    List<Match> lines = new ArrayList<>();
    BufferedImage bimg = Element.getBufferedImage(from);
    BufferedImage bimgResized = optimize(bimg);
    PageLevel pageLevel = toPageLevel(level);
    List<TextElement> textItems;
    try {
      textItems = getScribe().read(bimgResized, EnumSet.of(pageLevel)).elements(pageLevel);
    } catch (OctachorixFault e) {
      Debug.error("OCR: read: Octachorix: %s", e.getMessage());
      return lines;
    }
    double wFactor = (double) bimg.getWidth() / bimgResized.getWidth();
    double hFactor = (double) bimg.getHeight() / bimgResized.getHeight();
    for (TextElement textItem : textItems) {
      Rectangle boundingBox = textItem.bbox();
      Rectangle realBox = new Rectangle(
          (int) (boundingBox.x * wFactor) - 1,
          (int) (boundingBox.y * hFactor) - 1,
          1 + (int) (boundingBox.width * wFactor) + 2,
          1 + (int) (boundingBox.height * hFactor) + 2);
      lines.add(new Match(realBox, textItem.confidence(), textItem.text().trim()));
    }
    return lines;
  }
  //</editor-fold>

  //<editor-fold desc="99 obsolete">

  /**
   * @return the current screen resolution in dots per inch
   * @deprecated Will be removed in future versions<br>
   * use Toolkit.getDefaultToolkit().getScreenResolution()
   */
  @Deprecated
  public int getActualDPI() {
    return Toolkit.getDefaultToolkit().getScreenResolution();
  }

  /**
   * @param simg
   * @return the text read
   * @see OCR#readText(Object)
   * @deprecated use OCR.readText() instead
   */
  @Deprecated
  public String doOCR(ScreenImage simg) {
    return OCR.readText(simg);
  }

  /**
   * @param bimg
   * @return the text read
   * @see OCR#readText(Object)
   * @deprecated use OCR.readText() instead
   */
  @Deprecated
  public String doOCR(BufferedImage bimg) {
    return OCR.readText(bimg);
  }

  /**
   * @param simg
   * @return text
   * @see OCR#readText(Object)
   * @deprecated use OCR.readText() instead
   */
  @Deprecated
  public String recognize(ScreenImage simg) {
    BufferedImage bimg = simg.getImage();
    return OCR.readText(bimg);
  }

  /**
   * @param bimg
   * @return text
   * @see OCR#readText(Object)
   * @deprecated use OCR.readText() instead
   */
  @Deprecated
  public String recognize(BufferedImage bimg) {
    return OCR.readText(bimg);
  }
  //</editor-fold>

}
