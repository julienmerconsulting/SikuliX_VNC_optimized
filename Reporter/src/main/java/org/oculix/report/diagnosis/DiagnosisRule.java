package org.oculix.report.diagnosis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One regex-based diagnosis rule: pattern + metadata + how to build the
 * resulting {@link Diagnosis} when it matches.
 */
public final class DiagnosisRule {

    private final Pattern pattern;
    private final String category;
    private final String label;
    private final Diagnosis.Severity severity;
    private final String hint;

    public DiagnosisRule(Pattern pattern, String category, String label,
                         Diagnosis.Severity severity, String hint) {
        this.pattern = pattern;
        this.category = category;
        this.label = label;
        this.severity = severity;
        this.hint = hint;
    }

    public Diagnosis match(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        return new Diagnosis(category, label, severity, hint, m.group());
    }
}
