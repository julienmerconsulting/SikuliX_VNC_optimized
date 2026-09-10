/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the diagnosis text, which is the part a user actually reads when OCR fails.
 *
 * <p>The binding check itself needs a live JNA resolution and a real OCR call, so it is exercised
 * by running the app rather than here.
 */
public class NativeProvenanceTest {

  private static Path tierDirWith(String... fileNames) throws Exception {
    Path dir = Files.createTempDirectory("oculix-provenance");
    dir.toFile().deleteOnExit();
    for (String name : fileNames) {
      Path f = dir.resolve(name);
      Files.write(f, new byte[]{0});
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

  @Test
  public void unverifiedUntilAnOcrCallHasBeenObserved() {
    // Must never read as "fine" merely because nothing has been checked yet.
    assertFalse(NativeProvenance.isBindingVerified());
  }
}
