/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP client for an external PaddleOCR server (Python).
 *
 * Architecture:
 *   OculiX (Java) ---HTTP POST---> PaddleOCR (Python, localhost:5000)
 *
 * The Python server is an external prerequisite; it is NOT launched by this class.
 * If the server is unavailable, methods return empty results and callers
 * should fall back to Tesseract.
 *
 * Expected JSON format from the server:
 * <pre>
 * {
 *   "success": true,
 *   "recognition_time": 0.234,
 *   "results": [
 *     {
 *       "text": "Total",
 *       "confidence": 0.98,
 *       "bbox": [[10, 20], [80, 20], [80, 45], [10, 45]]
 *     }
 *   ]
 * }
 * </pre>
 *
 * bbox = 4 polygon points (top-left, top-right, bottom-right, bottom-left)
 *
 * JSON parsing via org.json (already in OculiX dependencies).
 *
 * @version 3.0
 */
public class PaddleOCRClient {

    // --- Configuration ---
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 35000;
    private static final int SERVER_CHECK_TIMEOUT_MS = 2000;

    private final String serverUrl;

    // --- Constructors ---

    public PaddleOCRClient(String host, int port) {
        this.serverUrl = "http://" + host + ":" + port;
    }

    public PaddleOCRClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    // =========================================================================
    // HEALTH CHECK
    // =========================================================================

    public boolean isServerAlive() {
        return isServerAlive(SERVER_CHECK_TIMEOUT_MS);
    }

    public boolean isServerAlive(int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + "/status");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                String response = readResponse(conn.getInputStream());
                return response.contains("\"status\"") && response.contains("\"ready\"");
            }
        } catch (Exception e) {
            // Silent for routine checks
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }

    public String getServerInfo() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + "/status");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(SERVER_CHECK_TIMEOUT_MS);
            conn.setReadTimeout(SERVER_CHECK_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == 200) {
                return readResponse(conn.getInputStream());
            }
        } catch (Exception e) {
            // Silent
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    // =========================================================================
    // OCR RECOGNITION
    // =========================================================================

    public String recognize(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return createErrorJson("Invalid image path");
        }

        File imageFile = new File(imagePath.trim());
        if (!imageFile.exists()) {
            return createErrorJson("Image not found: " + imagePath);
        }
        if (!imageFile.canRead()) {
            return createErrorJson("Cannot read image file: " + imagePath);
        }

        return performOCRRequest(imagePath);
    }

    public List<String> recognizeAndParseTexts(String imagePath) {
        String json = recognize(imagePath);
        return parseTextFromOCRResponse(json);
    }

    // =========================================================================
    // JSON PARSING (via org.json)
    // =========================================================================

    /**
     * Extract the list of texts from a PaddleOCR JSON response.
     */
    public static List<String> parseTextFromOCRResponse(String json) {
        List<String> texts = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return texts;

        try {
            JSONObject parsed = new JSONObject(json);
            if (!parsed.optBoolean("success", false)) return texts;

            JSONArray results = parsed.optJSONArray("results");
            if (results == null) return texts;

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                String text = item.optString("text", "").trim();
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] JSON parsing error: " + e.getMessage());
        }

        return texts;
    }

    /**
     * Extract texts with their confidence scores.
     */
    public static Map<String, Double> parseTextWithConfidence(String json) {
        Map<String, Double> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return result;

        try {
            JSONObject parsed = new JSONObject(json);
            if (!parsed.optBoolean("success", false)) return result;

            JSONArray results = parsed.optJSONArray("results");
            if (results == null) return result;

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                String text = item.optString("text", "").trim();
                double confidence = item.optDouble("confidence", 0.0);
                if (!text.isEmpty()) {
                    result.put(text, confidence);
                }
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] Confidence parsing error: " + e.getMessage());
        }

        return result;
    }

    // =========================================================================
    // COORDINATE SEARCH (for click automation)
    // =========================================================================

    /**
     * Find a text in OCR results and return its coordinates.
     * Case-insensitive containment search.
     *
     * @param json       raw JSON response
     * @param searchText text to find
     * @return int[] {x, y, width, height} or null if not found
     */
    public static int[] findTextCoordinates(String json, String searchText) {
        if (json == null || searchText == null) return null;
        try {
            JSONObject parsed = new JSONObject(json);
            if (!parsed.optBoolean("success", false)) return null;

            JSONArray results = parsed.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                String text = item.getString("text");
                if (text.toLowerCase().contains(searchText.toLowerCase())) {
                    return bboxToRect(item.getJSONArray("bbox"));
                }
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] findTextCoordinates error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Return ALL occurrences of a text with their coordinates.
     * Handles OCR whitespace artifacts ("AR TICHAUT" matches "ARTICHAUT").
     */
    public static List<int[]> findAllTextCoordinates(String json, String searchText) {
        List<int[]> matches = new ArrayList<>();
        if (json == null || searchText == null) return matches;

        try {
            JSONObject parsed = new JSONObject(json);
            if (!parsed.optBoolean("success", false)) return matches;

            JSONArray results = parsed.optJSONArray("results");
            if (results == null) return matches;

            String searchNoSpace = searchText.toLowerCase().replaceAll("\\s", "");

            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                String text = item.optString("text", "");
                String textNoSpace = text.toLowerCase().replaceAll("\\s", "");

                boolean isMatch = textNoSpace.contains(searchNoSpace)
                    || (text.length() >= 5 && searchNoSpace.contains(textNoSpace));

                if (isMatch) {
                    int[] rect = bboxToRect(item.getJSONArray("bbox"));
                    if (rect != null) matches.add(rect);
                }
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] findAllTextCoordinates error: " + e.getMessage());
        }
        return matches;
    }

    // =========================================================================
    // DEBUG / STATS
    // =========================================================================

    public static void printOCRStats(String json) {
        try {
            JSONObject parsed = new JSONObject(json);
            if (!parsed.optBoolean("success", false)) {
                System.out.println("[PaddleOCR] No stats (OCR failed)");
                return;
            }

            JSONArray results = parsed.optJSONArray("results");
            if (results == null) return;

            int total = results.length(), high = 0, medium = 0, low = 0;
            for (int i = 0; i < results.length(); i++) {
                double conf = results.getJSONObject(i).optDouble("confidence", 0.0);
                if (conf >= 0.9) high++;
                else if (conf >= 0.7) medium++;
                else low++;
            }

            System.out.println("[PaddleOCR] --- STATS ---");
            System.out.printf("[PaddleOCR]   Total: %d | High(>=90%%): %d | Med(70-90%%): %d | Low(<70%%): %d%n",
                total, high, medium, low);
            double time = parsed.optDouble("recognition_time", -1);
            if (time >= 0) {
                System.out.printf("[PaddleOCR]   Time: %.2fs%n", time);
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] Stats error: " + e.getMessage());
        }
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    /**
     * Convert a bbox JSONArray [[x1,y1],[x2,y2],[x3,y3],[x4,y4]] to {x, y, w, h}.
     */
    private static int[] bboxToRect(JSONArray bbox) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (int j = 0; j < bbox.length(); j++) {
            JSONArray point = bbox.getJSONArray(j);
            int px = point.getInt(0);
            int py = point.getInt(1);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }

        if (minX == Integer.MAX_VALUE) return null;
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    private String performOCRRequest(String imagePath) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(serverUrl + "/ocr");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setDoInput(true);

            String jsonPayload = new JSONObject().put("image_path", imagePath).toString();

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

            if (stream == null) {
                return createErrorJson("No response, HTTP " + code);
            }

            return readResponse(stream);

        } catch (Exception e) {
            return createErrorJson("HTTP Exception: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readResponse(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private static String createErrorJson(String message) {
        return new JSONObject()
            .put("success", false)
            .put("error", message)
            .put("results", new JSONArray())
            .toString();
    }

    public String getServerUrl() {
        return serverUrl;
    }
}
