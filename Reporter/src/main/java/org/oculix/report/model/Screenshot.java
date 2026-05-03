package org.oculix.report.model;

import java.time.Instant;

public final class Screenshot {
    private final String dataUri;
    private final Instant takenAt;
    private final String caption;
    private final int widthPx;
    private final int heightPx;

    public Screenshot(String dataUri, Instant takenAt, String caption, int widthPx, int heightPx) {
        this.dataUri = dataUri;
        this.takenAt = takenAt;
        this.caption = caption;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
    }

    public String dataUri() { return dataUri; }
    public Instant takenAt() { return takenAt; }
    public String caption() { return caption; }
    public int widthPx() { return widthPx; }
    public int heightPx() { return heightPx; }
}
