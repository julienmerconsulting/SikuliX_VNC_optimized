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
    Path previous = extractionDir;
    extractionDir = tierDir;
    if (previous != null && tierDir != null && !previous.equals(tierDir)) {
      // Worth surfacing: the first touch of Commons from anywhere triggers its static init, which
      // loads the natives and records the real tier directory — silently replacing whatever a
      // caller had set. Anything computed against the old value is now stale.
      Commons.startLog(3, "[OculiX] native extraction dir changed: %s -> %s", previous, tierDir);
    }
  }

  /**
   * Clears the one-shot latch so a test can exercise {@link #verifyBinding()} more than once.
   * Package-private and used only by tests — without it the binding flag can only be asserted at
   * its initial value, which is how a claim about it went untested in the first place.
   */
  static void resetForTest() {
    bindingChecked = false;
    bindingVerified = false;
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

  /**
   * The aliases missing from the extraction directory, or empty if nothing is needed.
   *
   * <p>Which names are required was measured across macOS, both Linux x86-64 tiers and native
   * Windows during the Legerix #20 investigation, and the rules are not symmetric:
   *
   * <ul>
   * <li><b>Unversioned only</b> for the JNA short-name lookup. A versioned alias would satisfy
   *     {@code isVersionedName} and enter JNA's version-pooling fallback, where it competes with
   *     whatever the distro ships — reintroducing the very bug this fixes.</li>
   * <li><b>Linux additionally needs versioned {@code liblept.so.5}</b>, because the bundled
   *     tesseract's ELF {@code NEEDED} is that literal string and no file of that name exists.
   *     It is safe only <em>alongside</em> the unversioned link, which wins the exact-name pass
   *     first so the pool is never consulted.</li>
   * <li><b>Windows needs nothing.</b> tess4j binds fully versioned names it ships itself, so the
   *     lookup this repairs never happens there.</li>
   * </ul>
   */
  /**
   * The aliases missing from a directory the caller has captured.
   *
   * <p>There is deliberately no no-argument form. The explicit parameter is the point: {@code extractionDir} is a mutable static, and the
   * first touch of {@link Commons} from anywhere triggers its static init, which loads the natives
   * and records the real tier directory — replacing whatever a caller had set. Reading the field
   * once per method meant a single logical operation could straddle two directories: the alias
   * names computed for one, the symlinks written into another. With a filesystem write as the
   * consequence, that is not a race worth leaving open.
   */
  public static List<String> missingAliases(Path dir) {
    List<String> missing = new ArrayList<>();
    if (dir == null || !Files.isDirectory(dir) || Commons.runningWindows()) {
      return missing;
    }
    try {
      for (String name : CONSUMER_NAMES) {
        String alias = System.mapLibraryName(name);
        if (!Files.exists(dir.resolve(alias)) && targetFor(dir, name) != null) {
          missing.add(alias);
        }
      }
      // Derived, not assumed. The dynamic linker's requirement is recorded inside the shipped
      // tesseract and is invisible in a directory listing — no file of that name exists, which is
      // precisely the defect. Reading DT_NEEDED answers it for whatever tier is actually present,
      // including the one where the name already matches and no alias is wanted at all.
      //
      // Evaluated independently of what the loop above found: gating it on the unversioned set
      // being incomplete would skip exactly the users who already followed our earlier
      // three-symlink advice, whose aliases exist so nothing looks "missing".
      Path tesseract = targetFor(dir, "tesseract");
      if (tesseract != null) {
        for (String need : readElfNeeded(tesseract)) {
          // Only dependencies this directory could satisfy. The rest of tesseract's NEEDED list is
          // libc, libstdc++ and friends, which are the system's business and not ours to alias.
          if (need.toLowerCase(Locale.ROOT).contains("lept") && !Files.exists(dir.resolve(need))) {
            missing.add(need);
          }
        }
      }
    } catch (Throwable ignore) {
    }
    return missing;
  }

  /** The command a user who declines can run themselves. Never leave them worse off for saying no. */
  /**
   * The command a user who declines can run themselves. Never leave them worse off for saying no.
   *
   * <p>Takes the directory explicitly, like everything else here. See {@link #missingAliases(Path)}
   * for why there is no convenience form that reads the field for you.
   */
  public static String manualCommand(Path dir) {
    List<String> missing = missingAliases(dir);
    if (dir == null || missing.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("cd ").append(dir).append(" && \\\n");
    for (int i = 0; i < missing.size(); i++) {
      String alias = missing.get(i);
      // A DT_NEEDED-derived alias always names a leptonica; the unversioned ones map by family.
      Path target = targetFor(dir, alias.toLowerCase(Locale.ROOT).contains("lept")
          && !isAliasName(alias.toLowerCase(Locale.ROOT)) ? "lept" : familyOf(alias));
      sb.append("  ln -s ").append(target == null ? "<library>" : target.getFileName())
          .append(' ').append(alias).append(i < missing.size() - 1 ? " && \\\n" : "\n");
    }
    return sb.toString();
  }

  /**
   * Creates the missing aliases and returns how many were made. Symlinks; falls back to a copy
   * where links are unavailable. Idempotent — if Legerix ever ships these itself, this does
   * nothing.
   */
  /**
   * Creates the missing aliases in a directory the caller has captured, returning how many were
   * made. Symlinks; falls back to a copy where links are unavailable. Idempotent.
   *
   * <p>Threading the path all the way through is what makes the guarantee real. Reading the static
   * inside this method still leaves a window between a caller's {@code recordExtraction} and the
   * call itself, because the first touch of {@link Commons} anywhere triggers its static init and
   * re-points the field. Demonstrated rather than imagined: a probe recorded a scratch directory,
   * received the correct alias list for it, and then wrote the alias into the real cache instead.
   */
  public static int createAliases(Path dir) {
    if (dir == null) {
      return 0;
    }
    int made = 0;
    for (String alias : missingAliases(dir)) {
      // A DT_NEEDED-derived alias always names a leptonica; the unversioned ones map by family.
      Path target = targetFor(dir, alias.toLowerCase(Locale.ROOT).contains("lept")
          && !isAliasName(alias.toLowerCase(Locale.ROOT)) ? "lept" : familyOf(alias));
      if (target == null) {
        continue;
      }
      Path link = dir.resolve(alias);
      try {
        try {
          Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | java.io.IOException linkFailed) {
          Files.copy(target, link);
        }
        made++;
        Commons.startLog(3, "[OculiX] created OCR native alias %s -> %s", alias,
            target.getFileName());
      } catch (Throwable e) {
        Commons.startLog(3, "[OculiX] could not create OCR native alias %s: %s", alias,
            e.getMessage());
      }
    }
    if (made > 0) {
      // The aliases only matter at resolution time, so a later OCR call still needs checking.
      bindingChecked = false;
    }
    return made;
  }

  /**
   * Reads the {@code DT_NEEDED} entries out of an ELF shared object.
   *
   * <p>This exists because a directory listing cannot answer the question. Two independent things
   * decide which aliases a tier needs: the short names a JNA consumer looks up, which are visible
   * as filenames; and the dynamic dependency recorded inside the shipped tesseract, which is
   * <em>not</em> — no file of that name exists, and that absence is the defect. Hardcoding it
   * happened to be right on three of the four Linux tiers and wrong on the fourth, and assuming a
   * SONAME rather than reading it is what cost an earlier report a retraction.
   *
   * <p>Returns an empty list for anything that is not a 64-bit little-endian ELF, which covers
   * every tier shipped today and means Mach-O simply yields nothing.
   */
  static List<String> readElfNeeded(Path file) {
    List<String> needed = new ArrayList<>();
    try {
      byte[] b = Files.readAllBytes(file);
      if (b.length < 64 || b[0] != 0x7F || b[1] != 'E' || b[2] != 'L' || b[3] != 'F'
          || b[4] != 2 || b[5] != 1) {
        return needed;                       // not 64-bit little-endian ELF
      }
      java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
      long phoff = buf.getLong(0x20);
      int phentsize = buf.getShort(0x36) & 0xFFFF;
      int phnum = buf.getShort(0x38) & 0xFFFF;

      long dynOff = -1;
      // PT_LOAD segments are what translate a virtual address into a file offset; DT_STRTAB is
      // recorded as a vaddr, so the mapping has to be built before the string table can be read.
      List<long[]> loads = new ArrayList<>();
      for (int i = 0; i < phnum; i++) {
        long ph = phoff + (long) i * phentsize;
        int type = buf.getInt((int) ph);
        long off = buf.getLong((int) ph + 0x08);
        long vaddr = buf.getLong((int) ph + 0x10);
        long filesz = buf.getLong((int) ph + 0x20);
        if (type == 2) {                     // PT_DYNAMIC
          dynOff = off;
        } else if (type == 1) {              // PT_LOAD
          loads.add(new long[]{vaddr, off, filesz});
        }
      }
      if (dynOff < 0) {
        return needed;
      }
      long strtabVaddr = -1;
      List<Long> neededOffsets = new ArrayList<>();
      for (long d = dynOff; d + 16 <= b.length; d += 16) {
        long tag = buf.getLong((int) d);
        long val = buf.getLong((int) d + 8);
        if (tag == 0) {                      // DT_NULL
          break;
        } else if (tag == 1) {               // DT_NEEDED — val is an index into DT_STRTAB
          neededOffsets.add(val);
        } else if (tag == 5) {               // DT_STRTAB
          strtabVaddr = val;
        }
      }
      if (strtabVaddr < 0) {
        return needed;
      }
      long strtabOff = -1;
      for (long[] l : loads) {
        if (strtabVaddr >= l[0] && strtabVaddr < l[0] + l[2]) {
          strtabOff = l[1] + (strtabVaddr - l[0]);
          break;
        }
      }
      if (strtabOff < 0) {
        return needed;
      }
      for (long idx : neededOffsets) {
        int start = (int) (strtabOff + idx);
        int end = start;
        while (end < b.length && b[end] != 0) {
          end++;
        }
        if (end > start) {
          needed.add(new String(b, start, end - start, java.nio.charset.StandardCharsets.UTF_8));
        }
      }
    } catch (Throwable e) {
      return new ArrayList<>();
    }
    return needed;
  }

  /**
   * True for any of the unversioned names this class creates. A versioned alias created from
   * {@code DT_NEEDED} needs no entry here: it is written as a symlink, and the directory scan
   * already excludes symlinks via {@code NOFOLLOW_LINKS}.
   */
  private static boolean isAliasName(String lowerName) {
    for (String n : CONSUMER_NAMES) {
      if (lowerName.equals(System.mapLibraryName(n).toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  /**
   * True if the file is a loadable object of this platform's own format.
   *
   * <p>Reads the magic bytes rather than matching the extension. Matching {@code .so} looked
   * equivalent and is not: Linux tiers ship {@code libtesseract.so.5} and {@code libleptonica.so.6},
   * neither of which <em>ends</em> with {@code .so}, so an extension test excluded every real
   * candidate and quietly made the whole repair inert on Linux while remaining correct on macOS.
   * Sniffing the format does what the restriction is actually for, and survives any future naming.
   */
  private static boolean isPlatformObject(Path file) {
    try (java.io.InputStream in = Files.newInputStream(file)) {
      byte[] m = new byte[4];
      if (in.read(m) < 4) {
        return false;
      }
      int b0 = m[0] & 0xFF, b1 = m[1] & 0xFF, b2 = m[2] & 0xFF, b3 = m[3] & 0xFF;
      if (Commons.runningWindows()) {
        return b0 == 'M' && b1 == 'Z';
      }
      if (Commons.runningMac()) {
        // Mach-O thin (either endianness, 32 or 64 bit) or a fat/universal archive.
        return (b0 == 0xCF || b0 == 0xCE) && b1 == 0xFA && b2 == 0xED && b3 == 0xFE
            || b0 == 0xFE && b1 == 0xED && b2 == 0xFA && (b3 == 0xCF || b3 == 0xCE)
            || b0 == 0xCA && b1 == 0xFE && b2 == 0xBA && b3 == 0xBE
            || b0 == 0xBE && b1 == 0xBA && b2 == 0xFE && b3 == 0xCA;
      }
      return b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F';
    } catch (Throwable e) {
      return false;
    }
  }

  private static String familyOf(String alias) {
    return alias.contains("leptonica") ? "leptonica" : alias.contains("lept") ? "lept" : "tesseract";
  }

  /** The real library an alias should point at: the versioned file already in the directory. */
  private static Path targetFor(Path dir, String name) {
    String want = name.equals("tesseract") ? "tesseract" : "lept";
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          // NOFOLLOW matters: an alias we made earlier in this same pass is itself a regular file
          // once resolved, and pointing a later alias at it would build a chain that breaks if the
          // middle link is removed. Aliases must always target the real versioned library.
          .filter(p -> Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS))
          .filter(p -> {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            // Exclude every name we might create, not just this one: on a tier that already ships
            // unversioned files (darwin does, darwin-aarch64 does not) an alias could otherwise be
            // pointed at another unversioned file, which is the indirection this is meant to stop.
            return n.contains(want) && !isAliasName(n);
          })
          .filter(NativeProvenance::isPlatformObject)
          // Deterministic: Files.list() has no defined order, and a directory with two candidates
          // would otherwise pick arbitrarily and fail intermittently rather than consistently.
          .sorted(java.util.Comparator.comparing(pp -> pp.getFileName().toString()))
          .findFirst().orElse(null);
    } catch (Throwable e) {
      return null;
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
