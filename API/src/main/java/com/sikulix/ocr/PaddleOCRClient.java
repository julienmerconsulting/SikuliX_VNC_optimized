/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

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
 *   SikuliX (Java) ---HTTP POST---> PaddleOCR (Python, localhost:5000)
 *
 * The Python server is an external prerequisite; it is NOT launched by this class.
 * If the server is unavailable, methods return empty results and callers
 * should fall back to Tesseract (OCR.readWords()).
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
 * Zero external dependencies - built-in JSON parsing.
 * If org.json is on the classpath, it is used automatically; otherwise
 * falls back to a minimal internal parser.
 *
 * @version 2.0
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

    /**
     * @param host PaddleOCR server host
     * @param port PaddleOCR server port
     */
    public PaddleOCRClient(String host, int port) {
        this.serverUrl = "http://" + host + ":" + port;
    }

    /** Default constructor: localhost:5000 */
    public PaddleOCRClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    // =========================================================================
    // HEALTH CHECK
    // =========================================================================

    /**
     * Check if the PaddleOCR server is responding.
     * @return true if the server is ready
     */
    public boolean isServerAlive() {
        return isServerAlive(SERVER_CHECK_TIMEOUT_MS);
    }

    /**
     * Check if the PaddleOCR server is responding with custom timeout.
     * @param timeoutMs timeout in milliseconds
     * @return true if the server responds with status "ready"
     */
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

    /**
     * Get server info (status, uptime).
     * @return raw JSON from /status, or null if unavailable
     */
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

    /**
     * Send an image to the PaddleOCR server and return the raw JSON.
     *
     * @param imagePath absolute path to a PNG/JPG image
     * @return raw JSON response
     */
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

    /**
     * Shortcut: recognize + extract texts in one operation.
     *
     * @param imagePath absolute path to the image
     * @return list of detected texts (empty if error)
     */
    public List<String> recognizeAndParseTexts(String imagePath) {
        String json = recognize(imagePath);
        return parseTextFromOCRResponse(json);
    }

    // =========================================================================
    // JSON PARSING
    // =========================================================================

    /**
     * Extract the list of texts from a PaddleOCR JSON response.
     *
     * @param json raw JSON response
     * @return list of detected texts (never null)
     */
    public static List<String> parseTextFromOCRResponse(String json) {
        List<String> texts = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return texts;

        try {
            Map<String, Object> parsed = parseJson(json);
            if (!Boolean.TRUE.equals(parsed.get("success"))) return texts;

            Object resultsObj = parsed.get("results");
            if (resultsObj instanceof List) {
                for (Object item : (List<?>) resultsObj) {
                    if (item instanceof Map) {
                        Object textObj = ((Map<?, ?>) item).get("text");
                        if (textObj != null && !textObj.toString().trim().isEmpty()) {
                            texts.add(textObj.toString().trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] JSON parsing error: " + e.getMessage());
        }

        return texts;
    }

    /**
     * Extract texts with their confidence scores.
     *
     * @param json raw JSON response
     * @return Map of text -> confidence (0.0 to 1.0)
     */
    public static Map<String, Double> parseTextWithConfidence(String json) {
        Map<String, Double> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return result;

        try {
            Map<String, Object> parsed = parseJson(json);
            if (!Boolean.TRUE.equals(parsed.get("success"))) return result;

            Object resultsObj = parsed.get("results");
            if (resultsObj instanceof List) {
                for (Object item : (List<?>) resultsObj) {
                    if (item instanceof Map) {
                        Map<?, ?> itemMap = (Map<?, ?>) item;
                        Object textObj = itemMap.get("text");
                        Object confObj = itemMap.get("confidence");
                        if (textObj != null && confObj != null) {
                            result.put(textObj.toString().trim(), ((Number) confObj).doubleValue());
                        }
                    }
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
            Map<String, Object> parsed = parseJson(json);
            if (!Boolean.TRUE.equals(parsed.get("success"))) return null;

            Object resultsObj = parsed.get("results");
            if (!(resultsObj instanceof List)) return null;

            for (Object item : (List<?>) resultsObj) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> itemMap = (Map<?, ?>) item;
                String text = String.valueOf(itemMap.get("text"));

                if (text.toLowerCase().contains(searchText.toLowerCase())) {
                    return extractBbox(itemMap);
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
     *
     * @param json       raw JSON response
     * @param searchText text to find
     * @return list of int[] {x, y, width, height} (never null)
     */
    public static List<int[]> findAllTextCoordinates(String json, String searchText) {
        List<int[]> matches = new ArrayList<>();
        if (json == null || searchText == null) return matches;

        try {
            Map<String, Object> parsed = parseJson(json);
            if (!Boolean.TRUE.equals(parsed.get("success"))) return matches;

            Object resultsObj = parsed.get("results");
            if (!(resultsObj instanceof List)) return matches;

            String searchNoSpace = searchText.toLowerCase().replaceAll("\\s", "");

            for (Object item : (List<?>) resultsObj) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> itemMap = (Map<?, ?>) item;
                String text = String.valueOf(itemMap.get("text"));
                String textNoSpace = text.toLowerCase().replaceAll("\\s", "");

                // Bidirectional match: "AR TICHAUT" contains "ARTICHAUT" and vice versa
                boolean isMatch = textNoSpace.contains(searchNoSpace)
                    || (text.length() >= 5 && searchNoSpace.contains(textNoSpace));

                if (isMatch) {
                    int[] bbox = extractBbox(itemMap);
                    if (bbox != null) matches.add(bbox);
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

    /**
     * Print OCR quality statistics (confidence, timing).
     *
     * @param json raw JSON response
     */
    public static void printOCRStats(String json) {
        try {
            Map<String, Object> parsed = parseJson(json);
            if (!Boolean.TRUE.equals(parsed.get("success"))) {
                System.out.println("[PaddleOCR] No stats (OCR failed)");
                return;
            }

            Object resultsObj = parsed.get("results");
            Object timeObj = parsed.get("recognition_time");

            if (!(resultsObj instanceof List)) return;
            List<?> results = (List<?>) resultsObj;

            int total = results.size(), high = 0, medium = 0, low = 0;
            for (Object item : results) {
                if (item instanceof Map) {
                    Object confObj = ((Map<?, ?>) item).get("confidence");
                    if (confObj != null) {
                        double conf = ((Number) confObj).doubleValue();
                        if (conf >= 0.9) high++;
                        else if (conf >= 0.7) medium++;
                        else low++;
                    }
                }
            }

            System.out.println("[PaddleOCR] --- STATS ---");
            System.out.printf("[PaddleOCR]   Total: %d | High(>=90%%): %d | Med(70-90%%): %d | Low(<70%%): %d%n",
                total, high, medium, low);
            if (timeObj != null) {
                System.out.printf("[PaddleOCR]   Time: %.2fs%n", ((Number) timeObj).doubleValue());
            }
        } catch (Exception e) {
            System.err.println("[PaddleOCR] Stats error: " + e.getMessage());
        }
    }

    // =========================================================================
    // INTERNALS
    // =========================================================================

    /** HTTP POST to /ocr */
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

            String jsonPayload = "{\"image_path\":\"" + escapeJson(imagePath) + "\"}";

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

    /** Read an InputStream to a UTF-8 String */
    private static String readResponse(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /** Generate an error JSON without external dependencies */
    private static String createErrorJson(String message) {
        return "{\"success\":false,\"error\":\"" + escapeJson(message) + "\",\"results\":[]}";
    }

    /** Escape special characters for JSON string insertion */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Parse JSON: uses org.json if available, otherwise minimal internal parser.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) {
        // Try org.json if present on the classpath
        try {
            Class<?> jsonObjClass = Class.forName("org.json.JSONObject");
            Object jsonObj = jsonObjClass.getConstructor(String.class).newInstance(json);
            return (Map<String, Object>) jsonObjClass.getMethod("toMap").invoke(jsonObj);
        } catch (ClassNotFoundException e) {
            // org.json not available, fallback to minimal parser
        } catch (Exception e) {
            System.err.println("[PaddleOCR] org.json parse error, fallback: " + e.getMessage());
        }

        // Fallback: minimal parser for the known PaddleOCR format
        return parseJsonMinimal(json);
    }

    /**
     * Minimal JSON parser covering strictly the PaddleOCR server format.
     * Does NOT handle generic JSON - only the known server response structure.
     * Replace with org.json as soon as possible.
     */
    private static Map<String, Object> parseJsonMinimal(String json) {
        Map<String, Object> result = new HashMap<>();
        // Detect success
        result.put("success", json.contains("\"success\":true") || json.contains("\"success\": true"));

        // Extract results (list of objects with text, confidence, bbox)
        List<Map<String, Object>> results = new ArrayList<>();
        int idx = json.indexOf("\"results\"");
        if (idx >= 0) {
            int arrStart = json.indexOf('[', idx);
            if (arrStart >= 0) {
                int depth = 0;
                int objStart = -1;
                for (int i = arrStart; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '{') {
                        if (depth == 1) objStart = i;
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 1 && objStart >= 0) {
                            String objStr = json.substring(objStart, i + 1);
                            results.add(parseResultItem(objStr));
                            objStart = -1;
                        }
                    } else if (c == ']' && depth == 1) {
                        break;
                    }
                }
            }
        }
        result.put("results", results);

        // Extract recognition_time
        int timeIdx = json.indexOf("\"recognition_time\"");
        if (timeIdx >= 0) {
            int colon = json.indexOf(':', timeIdx);
            if (colon >= 0) {
                StringBuilder numStr = new StringBuilder();
                for (int i = colon + 1; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (Character.isDigit(c) || c == '.') numStr.append(c);
                    else if (numStr.length() > 0) break;
                }
                if (numStr.length() > 0) {
                    result.put("recognition_time", Double.parseDouble(numStr.toString()));
                }
            }
        }

        return result;
    }

    /** Parse an OCR result item: {"text":"...", "confidence":0.98, "bbox":[[...], ...]} */
    private static Map<String, Object> parseResultItem(String objStr) {
        Map<String, Object> item = new HashMap<>();

        // Extract text
        String text = extractJsonString(objStr, "text");
        if (text != null) item.put("text", text);

        // Extract confidence
        int confIdx = objStr.indexOf("\"confidence\"");
        if (confIdx >= 0) {
            int colon = objStr.indexOf(':', confIdx);
            if (colon >= 0) {
                StringBuilder numStr = new StringBuilder();
                for (int i = colon + 1; i < objStr.length(); i++) {
                    char c = objStr.charAt(i);
                    if (Character.isDigit(c) || c == '.') numStr.append(c);
                    else if (numStr.length() > 0) break;
                }
                if (numStr.length() > 0) {
                    item.put("confidence", Double.parseDouble(numStr.toString()));
                }
            }
        }

        // Extract bbox: [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]
        int bboxIdx = objStr.indexOf("\"bbox\"");
        if (bboxIdx >= 0) {
            int arrStart = objStr.indexOf("[[", bboxIdx);
            int arrEnd = objStr.indexOf("]]", bboxIdx);
            if (arrStart >= 0 && arrEnd >= 0) {
                String bboxStr = objStr.substring(arrStart, arrEnd + 2);
                item.put("bbox", parseBbox(bboxStr));
            }
        }

        return item;
    }

    /** Parse [[x1,y1],[x2,y2],[x3,y3],[x4,y4]] into List<List<Integer>> */
    private static List<List<Integer>> parseBbox(String bboxStr) {
        List<List<Integer>> bbox = new ArrayList<>();
        String inner = bboxStr.substring(1, bboxStr.length() - 1);
        int depth = 0;
        int start = -1;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '[') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0 && start >= 0) {
                    String pair = inner.substring(start, i);
                    String[] parts = pair.split(",");
                    if (parts.length == 2) {
                        List<Integer> point = new ArrayList<>();
                        point.add((int) Double.parseDouble(parts[0].trim()));
                        point.add((int) Double.parseDouble(parts[1].trim()));
                        bbox.add(point);
                    }
                    start = -1;
                }
            }
        }
        return bbox;
    }

    /** Extract a JSON string value: "key":"value" */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int quoteStart = json.indexOf('"', colon + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = quoteStart + 1;
        while (quoteEnd < json.length()) {
            if (json.charAt(quoteEnd) == '"' && json.charAt(quoteEnd - 1) != '\\') break;
            quoteEnd++;
        }
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Extract a bounding box rectangle from an OCR result item.
     * Computes min/max over the 4 polygon points.
     *
     * @param itemMap one element from the "results" list
     * @return int[] {x, y, width, height} or null
     */
    @SuppressWarnings("unchecked")
    private static int[] extractBbox(Map<?, ?> itemMap) {
        Object bbox = itemMap.get("bbox");
        if (!(bbox instanceof List)) return null;

        List<?> bboxList = (List<?>) bbox;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        for (Object point : bboxList) {
            if (point instanceof List) {
                List<?> coords = (List<?>) point;
                int px = ((Number) coords.get(0)).intValue();
                int py = ((Number) coords.get(1)).intValue();
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
        }

        if (minX == Integer.MAX_VALUE) return null;
        return new int[]{minX, minY, maxX - minX, maxY - minY};
    }

    public String getServerUrl() {
        return serverUrl;
    }
}
