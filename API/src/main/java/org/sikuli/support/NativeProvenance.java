/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support;

import com.sun.jna.NativeLibrary;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Checks that the Tesseract our OCR calls actually reach is the one Legerix extracted for us.
 *
 * <p>Two distinct things can go wrong, and neither is visible from Legerix's own success:
 *
 * <ul>
 * <li><b>The consumer path binds a different library.</b> {@code Legerix.loadNatives()} loads its
 *     file by absolute path, which always succeeds. tess4j separately resolves the <em>short
 *     name</em> {@code tesseract} through JNA at its own static init, and can land on a system
 *     copy instead — silently, with OCR still returning plausible text. Legerix's own assertion
 *     cannot see this: it validates the file it extracted, not the one the consumer bound.</li>
 * <li><b>Legerix extracts our natives, not its own.</b> Its extraction reads
 *     {@code getProtectionDomain().getCodeSource()} — the jar containing {@code Legerix.class}.
 *     When we shade Legerix into a fat jar, that is <em>our</em> jar, so it extracts everything
 *     under the tier directory we ship, including natives Legerix never published.</li>
 * </ul>
 *
 * <p>Nothing here fails a startup or a capture. Wrong-library binding is a diagnosis problem, not
 * a crash, and the whole point is that it is currently invisible — so this reports and moves on.
 */
public class NativeProvenance {

  private NativeProvenance() {
  }

  /** Short names a consumer resolves through JNA; the ones that can silently go elsewhere. */
  private static final String[] CONSUMER_NAMES = {"tesseract", "leptonica", "lept"};

  private static volatile Path extractionDir;
  private static volatile boolean bindingChecked;
  private static volatile boolean bindingVerified;

  /**
   * Records where Legerix extracted to. {@code loadNatives()} returns the tier directory itself
   * (e.g. {@code ~/.cache/legerix/<version>/darwin-aarch64}); we previously discarded it.
   */
  public static void recordExtraction(Path tierDir) {
    extractionDir = tierDir;
  }

  public static Path getExtractionDir() {
    return extractionDir;
  }

  /**
   * True once a consumer OCR call has been checked and every short name resolved inside the
   * extraction directory. Stays false while unchecked, so callers must not read it as "fine".
   */
  public static boolean isBindingVerified() {
    return bindingVerified;
  }

  /**
   * Reports what Legerix put in the extraction directory, and whether it came from our jar.
   *
   * <p>The co-bundling test is an exact one rather than a heuristic: if Legerix's code source is
   * the same archive as ours, it necessarily extracted from our resources. Comparing file names
   * would misfire, because Legerix legitimately ships a large transitive set on Windows.
   */
  public static void auditExtraction(Class<?> legerixClass) {
    Path dir = extractionDir;
    if (dir == null || !Files.isDirectory(dir)) {
      return;
    }
    try {
      List<String> extracted = new ArrayList<>();
      try (Stream<Path> entries = Files.list(dir)) {
        entries.map(p -> p.getFileName().toString())
            .filter(n -> !n.equals(".gitkeep"))
            .sorted()
            .forEach(extracted::add);
      }
      Commons.startLog(3, "[OculiX] Legerix extracted %d file(s) to %s", extracted.size(), dir);

      if (!isShadedWithUs(legerixClass)) {
        return;
      }
      // Same archive: Legerix read our resource directory, so anything of ours sitting under the
      // tier name came along with its payload. Name the extras rather than guessing at intent.
      List<String> foreign = new ArrayList<>();
      for (String name : extracted) {
        if (!looksLikeTesseractFamily(name)) {
          foreign.add(name);
        }
      }
      if (!foreign.isEmpty()) {
        Commons.startLog(3, "[OculiX] Legerix is shaded into our jar, so it extracted from our "
            + "resources; %d file(s) there are not part of its own payload: %s",
            foreign.size(), String.join(", ", foreign));
      }
    } catch (Throwable e) {
      Commons.startLog(3, "[OculiX] native provenance audit skipped: %s: %s",
          e.getClass().getSimpleName(), e.getMessage());
    }
  }

  /**
   * Checks which library the consumer path actually bound, and reports any that resolved outside
   * the extraction directory.
   *
   * <p>Must run <em>after</em> a successful OCR call, for two reasons: tess4j only rewrites
   * {@code jna.library.path} during its own static init, so before that the check would prove
   * nothing; and asking JNA for a name it has not yet resolved would itself trigger a load, which
   * is the very thing under test. After OCR the instances are cached, so this only observes.
   */
  public static void verifyBinding() {
    if (bindingChecked) {
      return;
    }
    bindingChecked = true;
    Path dir = extractionDir;
    if (dir == null) {
      return;
    }
    boolean allInside = true;
    boolean anyObserved = false;
    for (String name : CONSUMER_NAMES) {
      String resolved = resolveIfAlreadyBound(name);
      if (resolved == null) {
        // Nothing bound this name in this JVM — on Windows tess4j uses fully versioned names and
        // never asks for these at all. Absence is not a failure, and it is not verification
        // either: see the flag below.
        continue;
      }
      anyObserved = true;
      if (isInside(dir, resolved)) {
        Commons.startLog(3, "[OculiX] OCR native '%s' -> %s (bundled)", name, resolved);
      } else if (matchesSomethingIn(dir, resolved)) {
        // Byte-identical content lowers the severity but must NOT clear the flag. The question
        // this check answers is one of PATH, not content: our directory still lost, and something
        // else is servicing OCR. Content equality cannot tell "our payload reached by another
        // route" from "a different build that happens to match" — and under a shaded jar the
        // directory's own contents may not be ours either. Treating it as a pass would return
        // success in precisely the case the check exists to detect.
        allInside = false;
        Commons.startLog(3, "[OculiX] OCR native '%s' -> %s : outside the bundled directory (%s), "
            + "though byte-identical to a file in it — most likely a consumer's own copy of our "
            + "payload rather than a different library.", name, resolved, dir);
      } else {
        allInside = false;
        Commons.startLog(3, "[OculiX] WARNING: the short name '%s' is bound to %s, which is "
            + "outside the bundled directory (%s) and not a copy of anything in it. Any consumer "
            + "resolving that name gets a library OculiX did not ship, and reported version "
            + "strings will not describe it.", name, resolved, dir);
      }
    }
    // Zero observations is NOT a pass. Every short name going unbound is the normal Windows case,
    // and treating it as verified would make this flag say "fine" on precisely the platform where
    // it has checked nothing at all.
    bindingVerified = anyObserved && allInside;
  }

  /**
   * Explains a JNA link failure in terms of what is actually on disk, or returns "" if we have
   * nothing useful to add.
   *
   * <p>The common case is not a broken or missing payload: it is that the extraction directory
   * holds only <em>versioned</em> filenames while JNA's exact-name lookup asks for the unversioned
   * one. Without a system copy to fall back on, that fails outright — and the generic
   * "reinstall" advice sends the user in the wrong direction entirely.
   */
  public static String explainLinkFailure() {
    Path dir = extractionDir;
    if (dir == null || !Files.isDirectory(dir)) {
      return "";
    }
    try {
      List<String> present = new ArrayList<>();
      try (Stream<Path> entries = Files.list(dir)) {
        entries.map(p -> p.getFileName().toString())
            .filter(NativeProvenance::looksLikeTesseractFamily)
            .sorted()
            .forEach(present::add);
      }
      if (present.isEmpty()) {
        return " The bundled directory " + dir + " contains no tesseract/leptonica library.\n";
      }
      String unversioned = System.mapLibraryName("tesseract");
      boolean hasUnversioned = present.stream().anyMatch(n -> n.equals(unversioned));
      StringBuilder sb = new StringBuilder();
      sb.append(" Bundled natives are present in ").append(dir).append(":\n   ")
          .append(String.join(", ", present)).append("\n");
      if (!hasUnversioned) {
        sb.append(" Note: JNA resolves the short name 'tesseract' to the exact filename '")
            .append(unversioned).append("', which is not among the files listed above.\n")
            .append(" Reinstalling will not change that.\n");
      }
      return sb.toString();
    } catch (Throwable e) {
      return "";
    }
  }

  /** Legerix and OculiX in one archive means its extraction read our resource directory. */
  private static boolean isShadedWithUs(Class<?> legerixClass) {
    URL ours = codeSource(NativeProvenance.class);
    URL theirs = codeSource(legerixClass);
    return ours != null && theirs != null && ours.toString().equals(theirs.toString());
  }

  private static URL codeSource(Class<?> clazz) {
    try {
      return clazz.getProtectionDomain().getCodeSource().getLocation();
    } catch (Throwable e) {
      return null;
    }
  }

  private static boolean looksLikeTesseractFamily(String fileName) {
    String n = fileName.toLowerCase(Locale.ROOT);
    return n.contains("tesseract") || n.contains("lept");
  }

  /**
   * Returns the absolute path JNA has <em>already</em> bound for a short name, or null if nothing
   * has bound it in this JVM.
   *
   * <p>Deliberately does not call {@code NativeLibrary.getInstance(name)}. That loads the library
   * when the name is not cached — which would perform the very short-name resolution this check
   * exists to observe, and could then report a library the consumer never touched as the one
   * servicing OCR. On Linux it is worse than misleading: speculatively mapping a system tesseract
   * alongside our bundled leptonica is exactly how the crash we reported upstream begins. A
   * diagnostic must not be able to cause the fault it looks for.
   *
   * <p>So this reads JNA's own cache and reports only what is genuinely there. Matching on
   * {@code getName()} rather than on the map key avoids depending on how JNA composes that key.
   * If the cache cannot be read, we report nothing rather than force a load.
   */
  private static String resolveIfAlreadyBound(String name) {
    try {
      java.lang.reflect.Field f = NativeLibrary.class.getDeclaredField("libraries");
      f.setAccessible(true);
      Object raw = f.get(null);
      if (!(raw instanceof java.util.Map)) {
        return null;
      }
      for (Object v : ((java.util.Map<?, ?>) raw).values()) {
        Object lib = (v instanceof java.lang.ref.Reference) ? ((java.lang.ref.Reference<?>) v).get() : v;
        if (!(lib instanceof NativeLibrary)) {
          continue;
        }
        NativeLibrary nl = (NativeLibrary) lib;
        if (name.equals(nl.getName())) {
          File file = nl.getFile();
          return file == null ? null : file.getCanonicalPath();
        }
      }
      return null;
    } catch (Throwable e) {
      return null;
    }
  }

  /**
   * True if the resolved file is byte-identical to one we extracted. Compares content rather than
   * name, because a consumer's copy is often renamed (tess4j binds {@code libtesseract552.dll}
   * where we ship {@code tesseract55.dll}). Size is checked first so the digest is rarely needed.
   */
  private static boolean matchesSomethingIn(Path dir, String resolvedPath) {
    try {
      Path resolved = Path.of(resolvedPath);
      long size = Files.size(resolved);
      List<Path> sameSize = new ArrayList<>();
      try (Stream<Path> entries = Files.list(dir)) {
        entries.filter(Files::isRegularFile).forEach(p -> {
          try {
            if (Files.size(p) == size) {
              sameSize.add(p);
            }
          } catch (Throwable ignore) {
          }
        });
      }
      if (sameSize.isEmpty()) {
        return false;
      }
      byte[] target = digest(resolved);
      for (Path candidate : sameSize) {
        if (java.util.Arrays.equals(target, digest(candidate))) {
          return true;
        }
      }
      return false;
    } catch (Throwable e) {
      return false;
    }
  }

  private static byte[] digest(Path file) throws Exception {
    return java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
  }

  private static boolean isInside(Path dir, String resolvedPath) {
    try {
      return Path.of(resolvedPath).toRealPath().startsWith(dir.toRealPath());
    } catch (Throwable e) {
      return false;
    }
  }
}
