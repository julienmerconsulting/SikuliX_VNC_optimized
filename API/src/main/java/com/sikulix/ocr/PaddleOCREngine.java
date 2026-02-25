/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.ocr;

import java.util.List;
import java.util.Map;

/**
 * High-precision OCR engine via an external PaddleOCR server.
 * Delegates to PaddleOCRClient for HTTP calls.
 *
 * The PaddleOCR server (Python) must be running externally.
 * If it is unavailable, isAvailable() returns false and the caller
 * should fall back to TesseractEngine.
 */
public class PaddleOCREngine implements OCREngine {

    private final PaddleOCRClient client;

    /**
     * Create a PaddleOCREngine pointing to a specific host and port.
     *
     * @param host PaddleOCR server host
     * @param port PaddleOCR server port
     */
    public PaddleOCREngine(String host, int port) {
        this.client = new PaddleOCRClient(host, port);
    }

    /**
     * Create a PaddleOCREngine with default settings (localhost:5000).
     */
    public PaddleOCREngine() {
        this.client = new PaddleOCRClient();
    }

    @Override
    public String getName() {
        return "paddleocr";
    }

    @Override
    public boolean isAvailable() {
        return client.isServerAlive();
    }

    @Override
    public String recognize(String imagePath) {
        return client.recognize(imagePath);
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

    /**
     * Get the underlying PaddleOCRClient.
     */
    public PaddleOCRClient getClient() {
        return client;
    }
}
