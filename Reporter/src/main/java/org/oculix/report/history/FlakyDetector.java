package org.oculix.report.history;

import org.oculix.report.model.Outcome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes a flakiness score per test from history and provides a
 * heuristic, human-readable explanation of <i>why</i> the test is flaky.
 * Score = flips / (runs - 1). {@link #isFlaky(double)} treats >= 0.3 as
 * flaky (test flips at least once every 3-4 runs).
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

    /**
     * Heuristic explanation of why a test looks flaky, derived from the
     * outcome sequence. Returns {@code null} if there isn't enough history.
     */
    public static String flakyReason(String testName, List<HistoryEntry> history) {
        List<Outcome> seq = new ArrayList<>();
        Outcome prev = null;
        int flips = 0;
        Set<Outcome> failingKinds = new LinkedHashSet<>();
        for (HistoryEntry e : history) {
            Outcome o = e.outcomes().get(testName);
            if (o == null) continue;
            seq.add(o);
            if (prev != null && prev != o) flips++;
            prev = o;
            if (o != Outcome.PASSED && o != Outcome.XPASSED && o != Outcome.SKIPPED) {
                failingKinds.add(o);
            }
        }
        int total = seq.size();
        if (total < 2) return null;

        StringBuilder s = new StringBuilder();
        s.append(flips).append(" outcome change").append(flips > 1 ? "s" : "")
         .append(" across ").append(total).append(" runs");

        if (failingKinds.size() >= 2) {
            s.append(" — multiple failure modes (")
             .append(failingKinds.stream().map(Outcome::slug).collect(Collectors.joining(", ")))
             .append("), environment or setup instability");
        } else if (failingKinds.size() == 1) {
            Outcome fail = failingKinds.iterator().next();
            s.append(" — flipping between passed and ").append(fail.slug());
            if (flips >= total - 1) {
                s.append(", almost every run: likely race condition or timing");
            } else {
                s.append(", consistent failure type");
            }
        }

        if (seq.size() >= 3) {
            boolean last3Green = true;
            for (int i = seq.size() - 3; i < seq.size(); i++) {
                if (seq.get(i) != Outcome.PASSED && seq.get(i) != Outcome.XPASSED) {
                    last3Green = false;
                    break;
                }
            }
            if (last3Green) s.append(". Last 3 runs green — may have stabilised");
        }

        return s.toString();
    }
}
