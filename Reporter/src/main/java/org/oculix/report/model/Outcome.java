package org.oculix.report.model;

public enum Outcome {
    PASSED("passed", "#22c55e"),
    FAILED("failed", "#ef4444"),
    ERROR("error", "#f97316"),
    SKIPPED("skipped", "#f59e0b"),
    XFAILED("xfailed", "#a855f7"),
    XPASSED("xpassed", "#06b6d4");

    private final String slug;
    private final String color;

    Outcome(String slug, String color) {
        this.slug = slug;
        this.color = color;
    }

    public String slug() { return slug; }
    public String color() { return color; }
}
