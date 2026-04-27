package org.oculix.report.render;

import org.oculix.report.model.Outcome;
import org.oculix.report.model.Screenshot;
import org.oculix.report.model.Step;
import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates a single-file HTML report from a {@link TestRun}.
 * CSS and JS are pulled from classpath resources and inlined, so the
 * output is fully self-contained: one file, no network, no missing
 * assets. Screenshots are already base64 data URIs in the model, so the
 * whole report is truly standalone.
 */
public final class HtmlRenderer {

    private static final String CSS_RESOURCE = "/org/oculix/report/reporter.css";
    private static final String JS_RESOURCE = "/org/oculix/report/reporter.js";

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public String render(TestRun run) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
          .append("<title>").append(esc(run.title())).append(" — OculiX Report</title>\n")
          .append("<style>").append(loadResource(CSS_RESOURCE)).append("</style>\n")
          .append("</head>\n<body>\n");

        sb.append("<div class=\"layout\">\n");
        renderSidebar(sb, run);
        sb.append("<main class=\"main\">\n");
        renderHeader(sb, run);
        renderOverview(sb, run);
        renderTiles(sb, run);
        renderMeta(sb, run);
        renderTests(sb, run);
        sb.append("</main>\n</div>\n");

        sb.append("<script>").append(loadResource(JS_RESOURCE)).append("</script>\n")
          .append("</body>\n</html>\n");
        return sb.toString();
    }

    public void renderTo(TestRun run, Path out) throws IOException {
        Files.writeString(out, render(run), StandardCharsets.UTF_8);
    }

    // ---- Sections ----

    private void renderSidebar(StringBuilder sb, TestRun run) {
        Map<Outcome, Integer> c = run.counts();
        sb.append("<aside class=\"sidebar\">\n")
          .append("<div class=\"sidebar-brand\"><h1>OculiX Report</h1>")
          .append("<span class=\"tagline\">").append(esc(run.title())).append("</span></div>\n")
          .append("<div class=\"sidebar-section-title\">Filter</div>\n")
          .append("<ul class=\"sidebar-nav\">\n")
          .append(navLink("all", "All", run.total(), true));
        for (Outcome o : Outcome.values()) {
            int n = c.getOrDefault(o, 0);
            if (n > 0) sb.append(navLink(o.slug(), capitalize(o.slug()), n, false));
        }
        sb.append("</ul>\n</aside>\n");
    }

    private String navLink(String slug, String label, int count, boolean active) {
        return "<li><a href=\"#\" data-filter=\"" + slug + "\""
            + (active ? " class=\"active\"" : "") + ">"
            + "<span>" + esc(label) + "</span>"
            + "<span class=\"nav-count\">" + count + "</span></a></li>\n";
    }

    private void renderHeader(StringBuilder sb, TestRun run) {
        sb.append("<div class=\"page-header\">\n")
          .append("<h2>").append(esc(run.title())).append("</h2>\n")
          .append("<div class=\"subtitle\">")
          .append(run.total()).append(" tests · ")
          .append(formatDuration(run.duration())).append(" · ")
          .append(String.format("%.1f%% success", run.successRate()))
          .append("</div>\n</div>\n");
    }

    private void renderOverview(StringBuilder sb, TestRun run) {
        Map<Outcome, Integer> c = run.counts();
        String timelineSvg = Timeline.generate(run);
        boolean hasTimeline = !timelineSvg.isEmpty();

        sb.append("<section class=\"overview")
          .append(hasTimeline ? "" : " no-timeline")
          .append("\">\n");

        // Donut column
        sb.append("<div class=\"overview-donut\">\n")
          .append(Donut.generate(c, 220))
          .append("<ul class=\"donut-legend\">\n");
        for (Outcome o : Outcome.values()) {
            int n = c.getOrDefault(o, 0);
            if (n == 0) continue;
            sb.append("<li>")
              .append("<span class=\"swatch\" style=\"background:").append(o.color()).append("\"></span>")
              .append("<span>").append(capitalize(o.slug())).append("</span>")
              .append("<span class=\"legend-count\">").append(n).append("</span>")
              .append("</li>\n");
        }
        sb.append("</ul>\n</div>\n");

        // Timeline column (skip if no timing data)
        if (hasTimeline) {
            sb.append("<div class=\"overview-timeline\">\n")
              .append("<h4>Timeline</h4>\n")
              .append(timelineSvg)
              .append("</div>\n");
        }

        sb.append("</section>\n");
    }

    private void renderTiles(StringBuilder sb, TestRun run) {
        Map<Outcome, Integer> c = run.counts();
        sb.append("<div class=\"tiles\">\n");
        sb.append("<div class=\"tile accent\"><div class=\"tile-value\">")
          .append(run.total()).append("</div><div class=\"tile-label\">Total</div></div>\n");
        for (Outcome o : Outcome.values()) {
            int n = c.getOrDefault(o, 0);
            if (n == 0) continue;
            sb.append("<div class=\"tile\" data-outcome=\"").append(o.slug()).append("\">")
              .append("<div class=\"tile-value\">").append(n).append("</div>")
              .append("<div class=\"tile-label\">").append(o.slug()).append("</div></div>\n");
        }
        sb.append("</div>\n");
    }

    private void renderMeta(StringBuilder sb, TestRun run) {
        sb.append("<div class=\"meta\">\n")
          .append(metaRow("Started", FMT.format(run.startedAt())))
          .append(metaRow("Duration", formatDuration(run.duration())))
          .append(metaRow("Environment", run.environment()))
          .append(metaRow("OculiX version", run.oculixVersion()))
          .append("</div>\n");
    }

    private String metaRow(String key, String val) {
        return "<div><span class=\"meta-key\">" + esc(key) + "</span>"
            + "<span class=\"meta-val mono\">" + esc(val) + "</span></div>\n";
    }

    private void renderTests(StringBuilder sb, TestRun run) {
        sb.append("<div class=\"section-title\"><h3>Tests</h3>")
          .append("<span class=\"subtitle\">Click a test to expand its steps</span></div>\n")
          .append("<div class=\"tests\">\n");
        for (Test t : run.tests()) renderTest(sb, t);
        sb.append("</div>\n");
    }

    private void renderTest(StringBuilder sb, Test t) {
        sb.append("<article class=\"test\" data-outcome=\"").append(t.outcome().slug()).append("\">\n")
          .append("<div class=\"test-header\">")
          .append("<span class=\"chevron\">&#9654;</span>")
          .append("<span class=\"badge\">").append(t.outcome().slug()).append("</span>")
          .append("<span class=\"test-name\">").append(esc(t.name())).append("</span>")
          .append("<span class=\"test-meta\">")
          .append("<span class=\"steps-count\">").append(t.steps().size()).append(" steps</span>")
          .append("<span class=\"duration\">").append(formatDuration(t.duration())).append("</span>")
          .append("</span></div>\n")
          .append("<div class=\"test-body\">\n");

        if (!t.errorMessage().isEmpty() || !t.stackTrace().isEmpty()) {
            sb.append("<div class=\"section\"><h4>Error</h4>");
            if (!t.errorMessage().isEmpty()) {
                sb.append("<pre>").append(esc(t.errorMessage())).append("</pre>");
            }
            if (!t.stackTrace().isEmpty()) {
                sb.append("<pre>").append(esc(t.stackTrace())).append("</pre>");
            }
            sb.append("</div>\n");
        }

        sb.append("<div class=\"section\"><h4>Steps</h4>\n<div class=\"steps\">\n");
        for (Step s : t.steps()) renderStep(sb, s);
        sb.append("</div></div>\n");

        sb.append("</div></article>\n");
    }

    private void renderStep(StringBuilder sb, Step s) {
        sb.append("<div class=\"step\" data-outcome=\"").append(s.outcome().slug()).append("\">\n")
          .append("<div class=\"step-head\">")
          .append("<span class=\"step-action\">").append(esc(s.action())).append("</span>")
          .append("<span class=\"step-target\">").append(esc(s.target())).append("</span>")
          .append("<span class=\"step-duration\">").append(formatDuration(s.duration())).append("</span>")
          .append("</div>\n");

        if (!s.errorMessage().isEmpty()) {
            sb.append("<div class=\"step-error\">").append(esc(s.errorMessage())).append("</div>\n");
        }

        if (!s.screenshots().isEmpty()) {
            sb.append("<div class=\"step-shots\">\n");
            for (Screenshot shot : s.screenshots()) {
                sb.append("<figure class=\"step-shot\">")
                  .append("<img src=\"").append(shot.dataUri()).append("\" alt=\"")
                  .append(esc(shot.caption())).append("\" loading=\"lazy\"/>")
                  .append("<figcaption>").append(esc(shot.caption())).append("</figcaption>")
                  .append("</figure>\n");
            }
            sb.append("</div>\n");
        }

        sb.append("</div>\n");
    }

    // ---- Helpers ----

    private static String loadResource(String path) {
        try (InputStream in = HtmlRenderer.class.getResourceAsStream(path)) {
            if (in == null) return "/* resource not found: " + path + " */";
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return r.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return "/* resource load error: " + e.getMessage() + " */";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':  out.append("&amp;");  break;
                case '<':  out.append("&lt;");   break;
                case '>':  out.append("&gt;");   break;
                case '"':  out.append("&quot;"); break;
                case '\'': out.append("&#39;");  break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private static String formatDuration(Duration d) {
        long millis = d.toMillis();
        if (millis < 1) return "< 1 ms";
        if (millis < 1000) return millis + " ms";
        if (millis < 60_000) return String.format("%.2f s", millis / 1000.0);
        long mins = millis / 60_000;
        double secs = (millis % 60_000) / 1000.0;
        return String.format("%dm %.1fs", mins, secs);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
