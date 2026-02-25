/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import com.sikulix.util.SikuliLogger;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/**
 * Default OCR engine using Tesseract integrated in SikuliX.
 * Always available, zero config, zero external dependency.
 * Less accurate than PaddleOCR but fully autonomous.
 *
 * Wraps org.sikuli.script.OCR and org.sikuli.script.TextRecognizer
 * to produce output in the unified JSON format used by OCREngine.
 */
public class TesseractEngine implements OCREngine {

    @Override
    public String getName() {
        return "tesseract";
    }

    @Override
    public boolean isAvailable() {
        // Tesseract is always available (bundled with SikuliX)
        return true;
    }

    @Override
    public String recognize(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return createErrorJson("Invalid image path");
        }

        try {
            File imageFile = new File(imagePath.trim());
            if (!imageFile.exists()) {
                return createErrorJson("Image not found: " + imagePath);
            }

            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                return createErrorJson("Cannot read image: " + imagePath);
            }

            // Use reflection to call SikuliX OCR without compile-time dependency
            // This allows the engine to work even if Tesseract isn't fully initialized
            return recognizeWithTesseract(image);

        } catch (Exception e) {
            SikuliLogger.error("[Tesseract] Recognition error: " + e.getMessage());
            return createErrorJson("Tesseract error: " + e.getMessage());
        }
    }

    /**
     * Perform Tesseract OCR using reflection to avoid hard dependency on SikuliX OCR classes.
     */
    private String recognizeWithTesseract(BufferedImage image) {
        long startTime = System.currentTimeMillis();

        try {
            // Try to use org.sikuli.script.OCR via reflection
            Class<?> ocrClass = Class.forName("org.sikuli.script.OCR");

            // Get OCR.readWords(BufferedImage)
            java.lang.reflect.Method readWordsMethod = null;
            for (java.lang.reflect.Method m : ocrClass.getMethods()) {
                if (m.getName().equals("readWords") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == BufferedImage.class) {
                    readWordsMethod = m;
                    break;
                }
            }

            // Fallback: try readText
            if (readWordsMethod == null) {
                java.lang.reflect.Method readTextMethod = null;
                for (java.lang.reflect.Method m : ocrClass.getMethods()) {
                    if (m.getName().equals("readText") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == BufferedImage.class) {
                        readTextMethod = m;
                        break;
                    }
                }

                if (readTextMethod != null) {
                    String text = (String) readTextMethod.invoke(null, image);
                    double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    return buildJsonFromText(text, elapsed);
                }
            }

            if (readWordsMethod != null) {
                @SuppressWarnings("unchecked")
                List<?> words = (List<?>) readWordsMethod.invoke(null, image);
                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                return buildJsonFromMatches(words, elapsed);
            }

            // Last resort: use Tess4J directly
            return recognizeWithTess4J(image, startTime);

        } catch (ClassNotFoundException e) {
            SikuliLogger.warn("[Tesseract] org.sikuli.script.OCR not found, trying Tess4J directly");
            return recognizeWithTess4J(image, startTime);
        } catch (Exception e) {
            SikuliLogger.error("[Tesseract] Reflection call failed: " + e.getMessage());
            return createErrorJson("Tesseract reflection error: " + e.getMessage());
        }
    }

    /**
     * Direct Tess4J usage as last resort.
     */
    private String recognizeWithTess4J(BufferedImage image, long startTime) {
        try {
            Class<?> tessClass = Class.forName("net.sourceforge.tess4j.Tesseract");
            Object tess = tessClass.getDeclaredConstructor().newInstance();

            java.lang.reflect.Method doOCR = tessClass.getMethod("doOCR", BufferedImage.class);
            String text = (String) doOCR.invoke(tess, image);

            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            return buildJsonFromText(text, elapsed);

        } catch (Exception e) {
            return createErrorJson("Tess4J unavailable: " + e.getMessage());
        }
    }

    /**
     * Build unified JSON from a list of Match objects (SikuliX).
     */
    private String buildJsonFromMatches(List<?> matches, double elapsedSeconds) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"engine\":\"tesseract\",\"recognition_time\":");
        json.append(String.format("%.3f", elapsedSeconds));
        json.append(",\"results\":[");

        boolean first = true;
        for (Object match : matches) {
            try {
                // Use reflection to access Match properties
                Class<?> matchClass = match.getClass();
                String text = "";
                double score = 0.5;
                int mx = 0, my = 0, mw = 0, mh = 0;

                try {
                    java.lang.reflect.Method getTextM = matchClass.getMethod("getText");
                    text = String.valueOf(getTextM.invoke(match));
                } catch (Exception ignored) {}

                try {
                    java.lang.reflect.Method getScoreM = matchClass.getMethod("getScore");
                    score = ((Number) getScoreM.invoke(match)).doubleValue();
                } catch (Exception ignored) {}

                try {
                    mx = ((Number) matchClass.getField("x").get(match)).intValue();
                    my = ((Number) matchClass.getField("y").get(match)).intValue();
                    mw = ((Number) matchClass.getField("w").get(match)).intValue();
                    mh = ((Number) matchClass.getField("h").get(match)).intValue();
                } catch (Exception ignored) {}

                if (text != null && !text.trim().isEmpty()) {
                    if (!first) json.append(",");
                    json.append("{\"text\":\"").append(escapeJson(text.trim()))
                        .append("\",\"confidence\":").append(String.format("%.2f", score))
                        .append(",\"bbox\":[[").append(mx).append(",").append(my)
                        .append("],[").append(mx + mw).append(",").append(my)
                        .append("],[").append(mx + mw).append(",").append(my + mh)
                        .append("],[").append(mx).append(",").append(my + mh)
                        .append("]]}");
                    first = false;
                }
            } catch (Exception ignored) {}
        }

        json.append("]}");
        return json.toString();
    }

    /**
     * Build unified JSON from raw OCR text (no bounding boxes).
     */
    private String buildJsonFromText(String text, double elapsedSeconds) {
        StringBuilder json = new StringBuilder();
        json.append("{\"success\":true,\"engine\":\"tesseract\",\"recognition_time\":");
        json.append(String.format("%.3f", elapsedSeconds));
        json.append(",\"results\":[");

        if (text != null && !text.trim().isEmpty()) {
            String[] lines = text.trim().split("\\n");
            boolean first = true;
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    if (!first) json.append(",");
                    json.append("{\"text\":\"").append(escapeJson(trimmed))
                        .append("\",\"confidence\":0.50")
                        .append(",\"bbox\":[[0,0],[100,0],[100,20],[0,20]]}");
                    first = false;
                }
            }
        }

        json.append("]}");
        return json.toString();
    }

    @Override
    public List<String> parseTexts(String json) {
        return PaddleOCRClient.parseTextFromOCRResponse(json);
    }

    @Override
    public int[] findTextCoordinates(String json, String searchText) {
        return PaddleOCRClient.findTextCoordinates(json, searchText);
    }

    @Override
    public List<int[]> findAllTextCoordinates(String json, String searchText) {
        return PaddleOCRClient.findAllTextCoordinates(json, searchText);
    }

    @Override
    public Map<String, Double> parseTextWithConfidence(String json) {
        return PaddleOCRClient.parseTextWithConfidence(json);
    }

    private static String createErrorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\",\"results\":[]}";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
