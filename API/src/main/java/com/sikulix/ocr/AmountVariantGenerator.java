/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates plausible variants of a monetary amount for OCR tolerance.
 *
 * Examples:
 * <pre>
 *   "30"  -> ["30", "0,30", "0.30", "30,00", "30.00"]
 *   "100" -> ["100", "1,00", "1.00", "100,00", "100.00"] (centimes -> euros)
 * </pre>
 *
 * This handles the common OCR problem where an amount might be detected
 * in different formats (with/without decimals, comma vs dot, centimes vs euros).
 */
public class AmountVariantGenerator {

    /**
     * Generate all plausible format variants of a monetary amount.
     *
     * @param amount the amount string (e.g., "30", "99.99", "0,50")
     * @return list of distinct variant strings
     */
    public static List<String> generate(String amount) {
        List<String> variants = new ArrayList<>();
        String base = amount.trim().replace("\u20AC", "").replace(" ", ""); // strip euro sign and spaces
        variants.add(base);

        if (!base.contains(",") && !base.contains(".")) {
            // Pure integer: add decimal variants
            variants.add("0," + base);
            variants.add("0." + base);
            variants.add(base + ",00");
            variants.add(base + ".00");

            // Conversion centimes -> euros if >= 100
            try {
                int centimes = Integer.parseInt(base);
                if (centimes >= 100) {
                    String euros = String.format("%.2f", centimes / 100.0);
                    variants.add(euros.replace(".", ","));
                    variants.add(euros);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Comma/dot swap
        if (base.contains(",")) variants.add(base.replace(",", "."));
        if (base.contains(".")) variants.add(base.replace(".", ","));

        return variants.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Compare two amounts with format tolerance.
     * "30" == "0,30" == "0.30" (within 0.01)
     *
     * @param expected the expected amount
     * @param detected the OCR-detected amount
     * @return true if the amounts are equivalent
     */
    public static boolean areEquivalent(String expected, String detected) {
        try {
            double a = parseAmount(expected);
            double b = parseAmount(detected);

            // Direct comparison
            if (Math.abs(a - b) < 0.01) return true;

            // Centimes -> euros conversion
            if (Math.abs((a / 100.0) - b) < 0.01) return true;
            if (Math.abs(a - (b / 100.0)) < 0.01) return true;

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse an amount string to double, handling both comma and dot as decimal separators.
     */
    private static double parseAmount(String amount) {
        String cleaned = amount.replace(",", ".").replaceAll("[^0-9.]", "");
        return Double.parseDouble(cleaned);
    }
}
