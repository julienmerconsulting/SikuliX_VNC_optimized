/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.util;

import java.text.Normalizer;

/**
 * Text normalization utilities for tolerant OCR comparison.
 *
 * OCR engines frequently produce slight variations in text output:
 * accents, punctuation differences, extra spaces, case variations.
 * This class provides normalization methods to handle these variations.
 */
public class TextNormalizer {

    /**
     * Normalize text for tolerant OCR comparison:
     * <ul>
     *   <li>Strip diacritical marks (accents)</li>
     *   <li>Lowercase</li>
     *   <li>Remove punctuation and spaces</li>
     * </ul>
     *
     * Example: "Reglement Especes" -> "reglementespeces"
     *
     * @param text the text to normalize
     * @return normalized text, or empty string if null
     */
    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Normalize text but preserve spaces (for multi-word comparison).
     *
     * Example: "Reglement Especes" -> "reglement especes"
     *
     * @param text the text to normalize
     * @return normalized text with spaces preserved
     */
    public static String normalizeKeepSpaces(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Check if two strings are equivalent after normalization.
     *
     * @param a first string
     * @param b second string
     * @return true if normalized versions are equal
     */
    public static boolean matches(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    /**
     * Check if normalized version of text contains normalized search string.
     *
     * @param text       the text to search in
     * @param searchText the text to search for
     * @return true if text contains searchText after normalization
     */
    public static boolean contains(String text, String searchText) {
        return normalize(text).contains(normalize(searchText));
    }
}
