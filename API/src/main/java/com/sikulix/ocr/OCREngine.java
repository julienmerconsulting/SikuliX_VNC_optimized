/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import java.util.List;
import java.util.Map;

/**
 * Common interface for all OCR engines.
 *
 * Provided implementations: TesseractEngine (default), PaddleOCREngine.
 * Extensible: implement this interface for EasyOCR, Google Vision, etc.
 *
 * All engines must return results in the unified JSON format:
 * <pre>
 * {
 *   "success": true,
 *   "engine": "paddleocr",
 *   "recognition_time": 0.234,
 *   "results": [
 *     {
 *       "text": "TOTAL",
 *       "confidence": 0.98,
 *       "bbox": [[10, 20], [80, 20], [80, 45], [10, 45]]
 *     }
 *   ]
 * }
 * </pre>
 *
 * bbox = 4 points of the polygon (top-left, top-right, bottom-right, bottom-left).
 * Conversion to rectangle: x = min(X), y = min(Y), w = max(X)-min(X), h = max(Y)-min(Y)
 */
public interface OCREngine {

    /** Engine name (for logging) */
    String getName();

    /** Check if the engine is available and ready */
    boolean isAvailable();

    /**
     * Perform OCR on an image.
     *
     * @param imagePath absolute path to a PNG/JPG image
     * @return raw JSON in the unified format
     */
    String recognize(String imagePath);

    /** Extract text strings from the JSON response */
    List<String> parseTexts(String json);

    /** First occurrence of a text -> {x, y, w, h} or null */
    int[] findTextCoordinates(String json, String searchText);

    /** All occurrences of a text -> list of {x, y, w, h} */
    List<int[]> findAllTextCoordinates(String json, String searchText);

    /** Texts with confidence scores */
    Map<String, Double> parseTextWithConfidence(String json);
}
