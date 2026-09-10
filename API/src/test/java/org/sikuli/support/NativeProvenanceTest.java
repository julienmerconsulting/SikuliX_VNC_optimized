/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Covers the diagnosis text, which is the part a user actually reads when OCR fails.
 *
 * <p>The binding check itself needs a live JNA resolution and a real OCR call, so it is exercised
 * by running the app rather than here.
 */
public class NativeProvenanceTest {

  /** The repair does not apply on Windows: tess4j binds versioned names it ships itself. */
  private static void assumeNotWindows() {
    assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
  }

  private static final byte[] ELF_MAGIC = {0x7F, 'E', 'L', 'F', 2, 1, 1, 0};
  private static final byte[] MACHO_MAGIC =
      {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE, 0, 0, 0, 0};

  private static Path tierDirWith(String... fileNames) throws Exception {
    Path dir = Files.createTempDirectory("oculix-provenance");
    dir.toFile().deleteOnExit();
    for (String name : fileNames) {
      Path f = dir.resolve(name);
      // Real magic bytes: targetFor() now sniffs the object format rather than trusting the
      // extension, so a fixture of zero bytes would be filtered out and test nothing.
      Files.write(f, name.endsWith(".dylib") ? MACHO_MAGIC : ELF_MAGIC);
      f.toFile().deleteOnExit();
    }
    NativeProvenance.recordExtraction(dir);
    return dir;
  }

  @Test
  public void noExtractionDirYieldsNoAdvice() {
    NativeProvenance.recordExtraction(null);
    assertEquals("", NativeProvenance.explainLinkFailure());
  }

  @Test
  public void namesTheDirectoryAndItsContents() throws Exception {
    Path dir = tierDirWith("libtesseract.5.dylib", "libleptonica.6.dylib");
    String advice = NativeProvenance.explainLinkFailure();
    assertTrue(advice.contains(dir.toString()));
    assertTrue(advice.contains("libtesseract.5.dylib"));
    assertTrue(advice.contains("libleptonica.6.dylib"));
  }

  @Test
  public void versionedOnlyPayloadExplainsTheExactNameMiss() throws Exception {
    tierDirWith("libtesseract.5.dylib", "libleptonica.6.dylib");
    String advice = NativeProvenance.explainLinkFailure();
    // The whole point: say why reinstalling will not help.
    assertTrue(advice.contains(System.mapLibraryName("tesseract")));
    assertTrue(advice.contains("Reinstalling will not change that"));
  }

  @Test
  public void unversionedAliasPresentSuppressesTheNote() throws Exception {
    tierDirWith("libtesseract.5.dylib", System.mapLibraryName("tesseract"));
    String advice = NativeProvenance.explainLinkFailure();
    assertFalse(advice.contains("Reinstalling will not change that"));
  }

  @Test
  public void emptyPayloadIsReportedAsSuch() throws Exception {
    tierDirWith();
    String advice = NativeProvenance.explainLinkFailure();
    assertTrue(advice.contains("contains no tesseract/leptonica library"));
  }

  /** A real tier ships one object format; a fixture mixing them tests something that cannot occur. */
  private static String[] versionedPayload() {
    return System.mapLibraryName("x").endsWith(".dylib")
        ? new String[]{"libtesseract.5.dylib", "libleptonica.6.dylib"}
        : new String[]{"libtesseract.so.5", "libleptonica.so.6"};
  }

  @Test
  public void versionedOnlyPayloadIsMissingTheUnversionedAliases() throws Exception {
    assumeNotWindows();
    Path dir = tierDirWith(versionedPayload());
    List<String> missing = NativeProvenance.missingAliases(dir);
    assertTrue(missing.contains(System.mapLibraryName("tesseract")));
    assertTrue(missing.contains(System.mapLibraryName("leptonica")));
    assertTrue(missing.contains(System.mapLibraryName("lept")));
    // Never propose a versioned alias for the JNA lookup: it would enter the version-pooling
    // fallback and compete with whatever the system ships, which is the bug being repaired.
    assertFalse(missing.contains("libtesseract.so.5"));
    assertFalse(missing.contains("libleptonica.6.dylib"));
  }

  @Test
  public void nothingMissingOnceTheAliasesExist() throws Exception {
    assumeNotWindows();
    Path dir = tierDirWith(versionedPayload());
    NativeProvenance.createAliases(dir);
    assertTrue(NativeProvenance.missingAliases(dir).isEmpty());
  }

  @Test
  public void aliasesTargetTheRealLibraryNeverAnotherAlias() throws Exception {
    assumeNotWindows();
    // Seed a tier that ALREADY has an unversioned file, as the darwin tier does. The earlier
    // version of this test could not have caught an alias pointing at another unversioned file,
    // because its fixture never contained one — the name promised more than the body checked.
    Path dir = tierDirWith(versionedPayload());
    Files.write(dir.resolve(System.mapLibraryName("leptonica")),
        System.mapLibraryName("x").endsWith(".dylib") ? MACHO_MAGIC : ELF_MAGIC);
    NativeProvenance.createAliases(dir);

    java.util.List<String> versioned = java.util.Arrays.asList(versionedPayload());
    for (String name : new String[]{"tesseract", "leptonica", "lept"}) {
      Path alias = dir.resolve(System.mapLibraryName(name));
      if (!Files.isSymbolicLink(alias)) {
        continue;
      }
      String target = Files.readSymbolicLink(alias).getFileName().toString();
      assertTrue(versioned.contains(target),
          "alias " + alias.getFileName() + " -> " + target
              + " : must point at a real versioned library, not " + versioned);
    }
  }

  @Test
  public void aliasTargetsNeverCrossObjectFormats() throws Exception {
    assumeNotWindows();
    // A directory holding both formats cannot occur in a shipped tier, but a hand-assembled one
    // can — and a .so aliased to a Mach-O binary fails only at load time, far from the cause.
    Path dir = tierDirWith("libtesseract.5.dylib", "libtesseract.so.5",
        "libleptonica.6.dylib", "libleptonica.so.6");
    NativeProvenance.createAliases(dir);
    String suffix = System.mapLibraryName("x").substring("libx".length());  // ".dylib" / ".so"
    for (String name : new String[]{"tesseract", "leptonica"}) {
      Path alias = dir.resolve(System.mapLibraryName(name));
      if (Files.isSymbolicLink(alias)) {
        String target = Files.readSymbolicLink(alias).getFileName().toString();
        assertTrue(target.endsWith(suffix),
            "alias " + alias.getFileName() + " -> " + target + " crosses object format");
      }
    }
  }

  @Test
  public void decliningStillYieldsARunnableCommand() throws Exception {
    assumeNotWindows();
    Path dir = tierDirWith("libtesseract.5.dylib", "libleptonica.6.dylib",
        "libtesseract.so.5", "libleptonica.so.6");
    String cmd = NativeProvenance.manualCommand(dir);
    assertTrue(cmd.contains("cd " + dir));
    assertTrue(cmd.contains("ln -s"));
  }

  @Test
  public void readElfNeededIgnoresAnythingThatIsNotElf() throws Exception {
    // Safety property: on macOS every file it meets is Mach-O, and on any platform it may be
    // handed a truncated or unrelated file. It must yield nothing rather than misparse — a wrong
    // DT_NEEDED would invent an alias, which is worse than deriving none.
    Path dir = Files.createTempDirectory("oculix-elf");
    dir.toFile().deleteOnExit();
    Path machO = dir.resolve("libfake.dylib");
    Files.write(machO, new byte[]{(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE, 0, 0, 0, 0});
    assertTrue(NativeProvenance.readElfNeeded(machO).isEmpty());

    Path truncated = dir.resolve("libtrunc.so");
    Files.write(truncated, new byte[]{0x7F, 'E', 'L', 'F', 2, 1});
    assertTrue(NativeProvenance.readElfNeeded(truncated).isEmpty());

    Path empty = dir.resolve("libempty.so");
    Files.write(empty, new byte[0]);
    assertTrue(NativeProvenance.readElfNeeded(empty).isEmpty());
  }

  @Test
  public void oneCallNeverStraddlesTwoDirectories() throws Exception {
    assumeNotWindows();
    // extractionDir is a mutable static that lazy init can re-point underneath a caller. The names
    // and the writes must therefore come from one captured value: a list computed for directory A
    // must never be written into directory B, because the consequence is files on disk.
    Path a = tierDirWith(versionedPayload());
    Path b = Files.createTempDirectory("oculix-other");
    b.toFile().deleteOnExit();

    java.util.List<String> forA = NativeProvenance.missingAliases(a);
    assertFalse(forA.isEmpty(), "fixture should need aliases");

    // Re-point the field, exactly as a lazy static init would, then create against the captured
    // path rather than whatever the field now says.
    NativeProvenance.recordExtraction(b);
    NativeProvenance.createAliases(a);

    for (String alias : forA) {
      assertTrue(Files.exists(a.resolve(alias)), "alias " + alias + " belongs in the recorded dir");
      assertFalse(Files.exists(b.resolve(alias)), "alias " + alias + " leaked into another dir");
    }
  }

  @Test
  public void unverifiedUntilAnOcrCallHasBeenObserved() {
    // Must never read as "fine" merely because nothing has been checked yet.
    NativeProvenance.resetForTest();
    assertFalse(NativeProvenance.isBindingVerified());
  }

  @Test
  public void zeroObservationsIsNotAPass() throws Exception {
    // The Windows case: tess4j binds fully versioned names it ships itself, so none of the short
    // names is ever bound and verifyBinding() observes nothing. Running it must not flip the flag
    // to true — an earlier version did exactly that, on the one platform it had checked nothing on.
    NativeProvenance.resetForTest();
    tierDirWith(versionedPayload());
    NativeProvenance.verifyBinding();
    assertFalse(NativeProvenance.isBindingVerified(),
        "verifyBinding() reported success without observing a single bound name");
  }
}
