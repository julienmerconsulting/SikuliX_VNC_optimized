package org.oculix.report.diagnosis;

/**
 * Result of running the diagnosis engine on a test failure. Carries a
 * human-readable label, an actionable hint, a category slug for grouping,
 * and a severity for visual styling.
 */
public final class Diagnosis {

    public enum Severity { INFO, WARNING, ERROR }

    private final String category;
    private final String label;
    private final Severity severity;
    private final String hint;
    private final String matchedText;

    public Diagnosis(String category, String label, Severity severity, String hint, String matchedText) {
        this.category = category;
        this.label = label;
        this.severity = severity;
        this.hint = hint;
        this.matchedText = matchedText == null ? "" : matchedText;
    }

    public String category()    { return category; }
    public String label()       { return label; }
    public Severity severity()  { return severity; }
    public String hint()        { return hint; }
    public String matchedText() { return matchedText; }
}
