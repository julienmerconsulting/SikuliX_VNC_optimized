package org.oculix.report.history;

import org.oculix.report.model.Outcome;

import java.util.List;

/**
 * Computes a flakiness score per test from history. A test is "flaky" if
 * its outcome flips often between consecutive runs — score = flips / (runs - 1),
 * capped at 1.0. {@link #isFlaky(double)} treats >= 0.3 as flaky (a test that
 * flips once every 3-4 runs).
 */
public final class FlakyDetector {

    public static final double FLAKY_THRESHOLD = 0.3;

    private FlakyDetector() {}

    public static double flakyScore(String testName, List<HistoryEntry> history) {
        Outcome prev = null;
        int flips = 0;
        int total = 0;
        for (HistoryEntry e : history) {
            Outcome o = e.outcomes().get(testName);
            if (o == null) continue;
            total++;
            if (prev != null && prev != o) flips++;
            prev = o;
        }
        return total < 2 ? 0.0 : (double) flips / (total - 1);
    }

    public static boolean isFlaky(double score) {
        return score >= FLAKY_THRESHOLD;
    }
}
