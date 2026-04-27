package org.oculix.report.render;

import org.oculix.report.model.Outcome;

import java.util.Locale;
import java.util.Map;

/**
 * SVG donut chart generator — pure stdlib, no deps.
 * Uses the stroke-dasharray technique (same approach as pytest-translate's
 * donut.py): a single thick circle per segment, with dasharray sized to
 * represent the segment's fraction of the total circumference.
 * Massively simpler and smaller than path-based arc drawing.
 */
public final class Donut {

    private Donut() {}

    public static String generate(Map<Outcome, Integer> counts, int size) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        double cx = size / 2.0;
        double cy = size / 2.0;
        double strokeWidth = size * 0.2;
        double r = size / 2.0 - strokeWidth / 2.0;
        double circumference = 2.0 * Math.PI * r;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg width=\"").append(size).append("\" height=\"").append(size)
          .append("\" viewBox=\"0 0 ").append(size).append(' ').append(size)
          .append("\" role=\"img\" aria-label=\"outcomes donut chart\">\n");

        if (total == 0) {
            sb.append(String.format(Locale.ROOT,
                "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" stroke=\"currentColor\" "
                    + "stroke-width=\"%.2f\" style=\"opacity:0.15\" fill=\"none\"></circle>\n",
                cx, cy, r, strokeWidth));
        } else {
            double offset = 0.0;
            for (Outcome o : Outcome.values()) {
                int v = counts.getOrDefault(o, 0);
                if (v <= 0) continue;
                double fraction = (double) v / total;
                double dash = fraction * circumference;
                double gap = circumference - dash;
                sb.append(String.format(Locale.ROOT,
                    "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" stroke=\"%s\" "
                        + "stroke-width=\"%.2f\" fill=\"none\" "
                        + "stroke-dasharray=\"%.3f %.3f\" stroke-dashoffset=\"%.3f\"></circle>\n",
                    cx, cy, r, o.color(), strokeWidth, dash, gap, -offset));
                offset += dash;
            }
        }

        // Center label: total count + "tests"
        sb.append(String.format(Locale.ROOT,
            "<text x=\"%.2f\" y=\"%.2f\" text-anchor=\"middle\" dominant-baseline=\"central\" "
                + "font-size=\"%.0f\" fill=\"currentColor\" font-weight=\"700\">%d</text>\n",
            cx, cy - size * 0.04, size * 0.22, total));
        sb.append(String.format(Locale.ROOT,
            "<text x=\"%.2f\" y=\"%.2f\" text-anchor=\"middle\" dominant-baseline=\"central\" "
                + "font-size=\"%.0f\" fill=\"currentColor\" fill-opacity=\"0.6\">tests</text>\n",
            cx, cy + size * 0.10, size * 0.06));

        sb.append("</svg>\n");
        return sb.toString();
    }
}
