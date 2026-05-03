package org.oculix.report.render;

import org.oculix.report.history.HistoryEntry;
import org.oculix.report.model.Outcome;

import java.util.List;
import java.util.Locale;

/**
 * Tiny inline SVG that shows a test's last N outcomes as colored cells —
 * one cell per past run, oldest left, newest right. Missing runs (test
 * not present) render as muted gray.
 */
public final class Sparkline {

    private static final int CELL_W = 6;
    private static final int CELL_H = 14;
    private static final int GAP    = 2;

    private Sparkline() {}

    public static String generate(String testName, List<HistoryEntry> history) {
        if (history == null || history.isEmpty()) return "";
        int n = history.size();
        int width = n * (CELL_W + GAP) - GAP;
        StringBuilder sb = new StringBuilder(64 + n * 120);
        sb.append("<svg class=\"sparkline\" width=\"").append(width)
          .append("\" height=\"").append(CELL_H)
          .append("\" viewBox=\"0 0 ").append(width).append(' ').append(CELL_H)
          .append("\" role=\"img\" aria-label=\"history sparkline\">");
        int x = 0;
        for (HistoryEntry e : history) {
            Outcome o = e.outcomes().get(testName);
            String fill = (o == null) ? "rgba(148,163,184,0.35)" : o.color();
            String label = (o == null) ? "not run" : o.slug();
            sb.append(String.format(Locale.ROOT,
                "<rect x=\"%d\" y=\"0\" width=\"%d\" height=\"%d\" rx=\"1.5\" fill=\"%s\">"
                    + "<title>%s — %s</title></rect>",
                x, CELL_W, CELL_H, fill, escape(e.runId()), label));
            x += CELL_W + GAP;
        }
        sb.append("</svg>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
