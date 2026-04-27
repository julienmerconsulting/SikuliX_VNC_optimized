package org.oculix.report.render;

import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Renders a "Top N slowest tests" panel as plain HTML — sorted bars where
 * the longest test sets the 100% mark and others scale relative to it.
 * Returns empty string if there's no usable timing data.
 */
public final class SlowestTests {

    private static final int DEFAULT_TOP_N = 8;

    private SlowestTests() {}

    public static String generate(TestRun run) {
        return generate(run, DEFAULT_TOP_N);
    }

    public static String generate(TestRun run, int topN) {
        List<Test> all = run.tests();
        if (all.isEmpty()) return "";

        List<Test> slowest = all.stream()
            .filter(t -> t.endedAt() != null && !t.duration().isZero())
            .sorted(Comparator.comparing(Test::duration).reversed())
            .limit(topN)
            .collect(Collectors.toList());

        if (slowest.isEmpty()) return "";

        long maxMs = Math.max(1, slowest.get(0).duration().toMillis());

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<ol class=\"slowest-list\">\n");
        for (Test t : slowest) {
            long ms = t.duration().toMillis();
            double pct = 100.0 * ms / maxMs;
            sb.append("<li class=\"slowest-row\" data-outcome=\"").append(t.outcome().slug()).append("\">")
              .append("<span class=\"slowest-name\" title=\"").append(escape(t.name())).append("\">")
              .append(escape(t.name())).append("</span>")
              .append("<div class=\"slowest-track\">")
              .append("<div class=\"slowest-bar\" style=\"width:")
              .append(String.format(Locale.ROOT, "%.2f", pct))
              .append("%;background:").append(t.outcome().color()).append("\"></div>")
              .append("</div>")
              .append("<span class=\"slowest-duration mono\">").append(formatDuration(t.duration())).append("</span>")
              .append("</li>\n");
        }
        sb.append("</ol>\n");
        return sb.toString();
    }

    private static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format(Locale.ROOT, "%.2f s", ms / 1000.0);
        return String.format(Locale.ROOT, "%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
