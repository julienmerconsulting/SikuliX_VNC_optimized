/*
 * Copyright (c) 2010-2026, sikuli.org, sikulix.com, oculix-org - MIT license
 */
package org.sikuli.support.recorder;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.sikuli.script.*;
import org.sikuli.support.Commons;

import java.awt.image.BufferedImage;

/**
 * Validates a captured pattern against a screenshot to detect ambiguity,
 * color dependency, and size issues before generating recorder code.
 *
 * Uses the existing Finder pipeline for cascade matching.
 */
public class PatternValidator {

  public enum Warning {
    NONE,
    AMBIGUOUS,
    COLOR_DEPENDENT,
    TOO_SMALL
  }

  public static class ValidationResult {
    public final int matchCount;
    public final double bestScore;
    public final double suggestedSimilarity;
    public final Warning warning;

    public ValidationResult(int matchCount, double bestScore, double suggestedSimilarity, Warning warning) {
      this.matchCount = matchCount;
      this.bestScore = bestScore;
      this.suggestedSimilarity = suggestedSimilarity;
      this.warning = warning;
    }
  }

  /**
   * Validate a captured pattern against a full screenshot.
   *
   * @param screenshot the full screen image to search in
   * @param candidate  the captured region image to validate
   * @return validation result with match count, score, and warnings
   */
  public static ValidationResult validate(BufferedImage screenshot, BufferedImage candidate) {
    if (screenshot == null || candidate == null) {
      return new ValidationResult(0, 0, 0.7, Warning.NONE);
    }

    // Check if pattern is too small
    if (candidate.getWidth() < 10 || candidate.getHeight() < 10) {
      return new ValidationResult(0, 0, 0.7, Warning.TOO_SMALL);
    }

    // Find all matches at default similarity (0.7)
    Image candidateImage = new Image(candidate);
    Pattern pattern = new Pattern(candidateImage).similar(0.7f);

    int matchCount = 0;
    double bestScore = 0;

    try {
      Finder finder = new Finder(screenshot);
      finder.findAll(pattern);
      while (finder.hasNext()) {
        Match m = finder.next();
        matchCount++;
        if (m.getScore() > bestScore) {
          bestScore = m.getScore();
        }
      }
    } catch (Exception e) {
      System.err.println("[PatternValidator] Find error: " + e.getMessage());
      return new ValidationResult(0, 0, 0.7, Warning.NONE);
    }

    // If multiple matches, suggest higher similarity
    if (matchCount > 1) {
      double suggested = Math.min(bestScore + 0.05, 0.99);
      return new ValidationResult(matchCount, bestScore,
          Math.round(suggested * 100.0) / 100.0, Warning.AMBIGUOUS);
    }

    // Check color dependency: compare match in grayscale
    if (matchCount == 1) {
      try {
        boolean colorDependent = isColorDependent(screenshot, candidate, bestScore);
        if (colorDependent) {
          return new ValidationResult(matchCount, bestScore, 0.7, Warning.COLOR_DEPENDENT);
        }
      } catch (Exception e) {
        // Grayscale check failed, skip silently
      }
    }

    return new ValidationResult(matchCount, bestScore, 0.7, Warning.NONE);
  }

  /**
   * Checks if the pattern depends on color by comparing scores in grayscale.
   * If the grayscale score drops significantly, the pattern is color-dependent.
   */
  private static boolean isColorDependent(BufferedImage screenshot, BufferedImage candidate, double colorScore) {
    Mat screenMat = Commons.makeMat(screenshot, false);
    Mat candidateMat = Commons.makeMat(candidate, false);

    if (screenMat.empty() || candidateMat.empty()) return false;

    Mat screenGray = new Mat();
    Mat candidateGray = new Mat();
    Imgproc.cvtColor(screenMat, screenGray, Imgproc.COLOR_BGR2GRAY);
    Imgproc.cvtColor(candidateMat, candidateGray, Imgproc.COLOR_BGR2GRAY);

    // Convert grayscale Mats back to BufferedImage for Finder
    BufferedImage screenGrayImg = Commons.getBufferedImage(screenGray);
    BufferedImage candidateGrayImg = Commons.getBufferedImage(candidateGray);

    if (screenGrayImg == null || candidateGrayImg == null) return false;

    Image grayImage = new Image(candidateGrayImg);
    Pattern grayPattern = new Pattern(grayImage).similar(0.7f);

    double grayScore = 0;
    try {
      Finder finder = new Finder(screenGrayImg);
      finder.find(grayPattern);
      if (finder.hasNext()) {
        grayScore = finder.next().getScore();
      }
    } catch (Exception e) {
      return false;
    }

    // If gray score drops more than 15% from color score, it's color-dependent
    return (colorScore - grayScore) > 0.15;
  }
}
