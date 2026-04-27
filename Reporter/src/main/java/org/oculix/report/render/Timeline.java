package org.oculix.report.render;

import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * SVG Gantt timeline of tests on a horizontal time axis.
 * Helps spot long-running tests at a glance. Pure stdlib, no deps.
 * Returns empty string if the run has no usable timing data.
 */
public final class Timeline {

    private static final int WIDTH = 1000;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_GAP = 4;
    private static final int LEFT_PAD = 240;
    private static final int RIGHT_PAD = 20;
    private static final int TOP_PAD = 20;

    private Timeline() {}

    public static String generate(TestRun run) {
        List<Test> tests = run.tests();
        if (tests.isEmpty()) return "";
        Duration total = run.duration();
        if (total.isZero() || total.isNegative()) return "";

        long totalMs = total.toMillis();
        if (totalMs <= 0) return "";

        Instant origin = run.startedAt();
        int chartWidth = WIDTH - LEFT_PAD - RIGHT_PAD;
        int height = TOP_PAD + tests.size() * (ROW_HEIGHT + ROW_GAP) + 10;

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<svg viewBox=\"0 0 ").append(WIDTH).append(' ').append(height)
          .append("\" role=\"img\" aria-label=\"tests timeline\">\n");

        // Axis line
        int axisY = TOP_PAD - 6;
        sb.append(String.format(Locale.ROOT,
            "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"currentColor\" "
                + "stroke-opacity=\"0.2\" stroke-width=\"1\"></line>\n",
            LEFT_PAD, axisY, WIDTH - RIGHT_PAD, axisY));

        // Tick labels at 0, 25, 50, 75, 100%
        for (int pct = 0; pct <= 100; pct += 25) {
            int tx = LEFT_PAD + chartWidth * pct / 100;
            long tMs = totalMs * pct / 100;
            sb.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"currentColor\" "
                    + "fill-opacity=\"0.5\" text-anchor=\"middle\">%s</text>\n",
                tx, axisY - 4, formatTick(tMs)));
            sb.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"currentColor\" "
                    + "stroke-opacity=\"0.1\" stroke-width=\"1\"></line>\n",
                tx, axisY, tx, height - 10));
        }

        // Rows
        int y = TOP_PAD;
        for (Test t : tests) {
            if (t.startedAt() == null || t.endedAt() == null) {
                y += ROW_HEIGHT + ROW_GAP;
                continue;
            }
            long startMs = Duration.between(origin, t.startedAt()).toMillis();
            long durMs = Math.max(1, t.duration().toMillis());
            int x = LEFT_PAD + (int) (chartWidth * startMs / totalMs);
            int w = Math.max(2, (int) (chartWidth * durMs / totalMs));

            // Test name (truncated)
            String name = t.name();
            if (name.length() > 34) name = name.substring(0, 31) + "...";
            sb.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"%d\" font-size=\"10\" fill=\"currentColor\" "
                    + "fill-opacity=\"0.75\" text-anchor=\"end\">%s</text>\n",
                LEFT_PAD - 8, y + ROW_HEIGHT - 4, escape(name)));

            // Bar
            sb.append(String.format(Locale.ROOT,
                "<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"2\" ry=\"2\" fill=\"%s\">"
                    + "<title>%s — %s</title></rect>\n",
                x, y, w, ROW_HEIGHT, t.outcome().color(),
                escape(t.name()), formatDuration(t.duration())));

            y += ROW_HEIGHT + ROW_GAP;
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    private static String formatTick(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
        return (ms / 60_000) + "m";
    }

    private static String formatDuration(Duration d) {
        long ms = d.toMillis();
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format(Locale.ROOT, "%.2f s", ms / 1000.0);
        return String.format(Locale.ROOT, "%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
