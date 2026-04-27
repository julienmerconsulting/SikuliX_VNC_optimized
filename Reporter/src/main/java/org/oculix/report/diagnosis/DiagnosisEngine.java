package org.oculix.report.diagnosis;

import java.util.List;

/**
 * Applies an ordered list of {@link DiagnosisRule}s to a test failure
 * (error message + stack trace concatenated). Returns the first match,
 * or {@code null} if no rule fires — callers should treat null as
 * "unclassified".
 */
public final class DiagnosisEngine {

    private static final DiagnosisEngine DEFAULT = new DiagnosisEngine(DiagnosisRules.DEFAULTS);

    private final List<DiagnosisRule> rules;

    public DiagnosisEngine(List<DiagnosisRule> rules) {
        this.rules = rules;
    }

    public static DiagnosisEngine defaultEngine() {
        return DEFAULT;
    }

    public Diagnosis diagnose(String errorMessage, String stackTrace) {
        if ((errorMessage == null || errorMessage.isEmpty())
            && (stackTrace == null || stackTrace.isEmpty())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (errorMessage != null) sb.append(errorMessage);
        if (stackTrace != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(stackTrace);
        }
        String haystack = sb.toString();
        for (DiagnosisRule rule : rules) {
            Diagnosis d = rule.match(haystack);
            if (d != null) return d;
        }
        return null;
    }
}
