package org.oculix.report.render;

import org.oculix.report.diagnosis.Diagnosis;
import org.oculix.report.diagnosis.DiagnosisEngine;
import org.oculix.report.history.FlakyDetector;
import org.oculix.report.history.HistoryEntry;
import org.oculix.report.history.HistoryStore;
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
import java.util.Locale;
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
    private static final DiagnosisEngine DIAGNOSIS = DiagnosisEngine.defaultEngine();

    private HistoryStore history;

    public HtmlRenderer withHistory(HistoryStore history) {
        this.history = history;
        return this;
    }

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
        renderTrends(sb, run);
        renderTiles(sb, run);
        renderMeta(sb, run);
        renderTests(sb, run);
        sb.append("</main>\n</div>\n");

        // Modal overlay (populated dynamically by JS when a slowest-row is clicked)
        sb.append("<div class=\"test-modal-overlay\" hidden role=\"dialog\" aria-modal=\"true\" aria-label=\"Test details\">\n")
          .append("<div class=\"test-modal\">\n")
          .append("<button class=\"test-modal-close\" aria-label=\"Close\" type=\"button\">&times;</button>\n")
          .append("<div class=\"test-modal-body\"></div>\n")
          .append("</div>\n</div>\n");

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
          .append("<div class=\"sidebar-section-title\">Search</div>\n")
          .append("<input type=\"search\" class=\"sidebar-search\" id=\"sidebar-search\" "
              + "placeholder=\"Test name…\" autocomplete=\"off\">\n")
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
          .append(String.format(Locale.ROOT, "%.1f%% success", run.successRate()))
          .append("</div>\n</div>\n");
    }

    private void renderOverview(StringBuilder sb, TestRun run) {
        Map<Outcome, Integer> c = run.counts();
        String slowestHtml = SlowestTests.generate(run);
        boolean hasSlowest = !slowestHtml.isEmpty();

        sb.append("<section class=\"overview")
          .append(hasSlowest ? "" : " no-slowest")
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

        // Slowest tests column (skip if no timing data)
        if (hasSlowest) {
            sb.append("<div class=\"overview-slowest\">\n")
              .append("<h4>Slowest tests</h4>\n")
              .append(slowestHtml)
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

    private void renderTrends(StringBuilder sb, TestRun run) {
        if (history == null || history.isEmpty()) return;
        HistoryEntry prev = history.entries().get(history.entries().size() - 1);
        int regressions = 0, newPasses = 0, flakyCount = 0;
        for (Test t : run.tests()) {
            Outcome cur = t.outcome();
            Outcome prevOut = prev.outcomes().get(t.name());
            if (prevOut != null) {
                boolean prevPassed = prevOut == Outcome.PASSED || prevOut == Outcome.XPASSED;
                boolean curPassed = cur == Outcome.PASSED || cur == Outcome.XPASSED;
                if (prevPassed && !curPassed) regressions++;
                else if (!prevPassed && curPassed) newPasses++;
            }
            if (FlakyDetector.isFlaky(FlakyDetector.flakyScore(t.name(), history.entries()))) {
                flakyCount++;
            }
        }
        if (regressions == 0 && newPasses == 0 && flakyCount == 0) return;
        sb.append("<div class=\"trends\">\n")
          .append("<span class=\"trends-label\">vs previous run</span>");
        if (regressions > 0) {
            sb.append("<span class=\"trend-pill trend-regression\">")
              .append("<span class=\"trend-arrow\">▾</span>")
              .append(regressions).append(" regression").append(regressions > 1 ? "s" : "").append("</span>");
        }
        if (newPasses > 0) {
            sb.append("<span class=\"trend-pill trend-improvement\">")
              .append("<span class=\"trend-arrow\">▴</span>")
              .append(newPasses).append(" new pass").append(newPasses > 1 ? "es" : "").append("</span>");
        }
        if (flakyCount > 0) {
            sb.append("<span class=\"trend-pill trend-flaky\">")
              .append(flakyCount).append(" flaky").append("</span>");
        }
        sb.append("\n</div>\n");
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
          .append("<div class=\"tests-toolbar\">\n")
          .append("<span class=\"toolbar-group\">")
          .append("<span class=\"toolbar-label\">Sort by</span>")
          .append("<button type=\"button\" class=\"sort-btn\" data-sort=\"name\">Name</button>")
          .append("<button type=\"button\" class=\"sort-btn\" data-sort=\"duration\">Duration</button>")
          .append("<button type=\"button\" class=\"sort-btn\" data-sort=\"outcome\">Outcome</button>")
          .append("</span>")
          .append("<span class=\"toolbar-group\">")
          .append("<button type=\"button\" class=\"action-btn\" data-action=\"expand-all\">Expand all</button>")
          .append("<button type=\"button\" class=\"action-btn\" data-action=\"collapse-all\">Collapse all</button>")
          .append("</span>\n")
          .append("</div>\n")
          .append("<div class=\"tests\">\n");
        int i = 0;
        for (Test t : run.tests()) renderTest(sb, t, i++);
        sb.append("</div>\n");
    }

    private void renderTest(StringBuilder sb, Test t, int index) {
        int shotCount = 0;
        for (Step s : t.steps()) shotCount += s.screenshots().size();
        long durationMs = t.duration().toMillis();

        boolean haveHistory = history != null && !history.isEmpty();
        double flakyScore = haveHistory ? FlakyDetector.flakyScore(t.name(), history.entries()) : 0.0;
        boolean flaky = FlakyDetector.isFlaky(flakyScore);

        sb.append("<article class=\"test\" id=\"test-").append(index)
          .append("\" data-outcome=\"").append(t.outcome().slug())
          .append("\" data-test-index=\"").append(index)
          .append("\" data-name=\"").append(esc(t.name()))
          .append("\" data-duration-ms=\"").append(durationMs)
          .append("\" data-flaky=\"").append(flaky).append("\">\n")
          .append("<div class=\"test-header\">")
          .append("<span class=\"chevron\">&#9654;</span>")
          .append("<span class=\"badge\">").append(t.outcome().slug()).append("</span>")
          .append("<span class=\"test-name\">").append(esc(t.name())).append("</span>");
        if (flaky) {
            sb.append("<span class=\"flaky-badge\" title=\"Flaky score ")
              .append(String.format(Locale.ROOT, "%.0f%%", flakyScore * 100))
              .append("\">flaky</span>");
        }
        sb.append("<span class=\"test-meta\">")
          .append("<span class=\"steps-count\">").append(t.steps().size()).append(" steps</span>");
        if (shotCount > 0) {
            sb.append("<span class=\"shots-count\">").append(shotCount).append(" shots</span>");
        }
        if (haveHistory) {
            sb.append("<span class=\"test-history\">")
              .append(Sparkline.generate(t.name(), history.entries()))
              .append("</span>");
        }
        sb.append("<span class=\"duration\">").append(formatDuration(t.duration())).append("</span>")
          .append("<a class=\"test-permalink\" href=\"#test-").append(index)
          .append("\" title=\"Copy permalink\" aria-label=\"Permalink\">#</a>")
          .append("</span></div>\n")
          .append("<div class=\"test-body\">\n");

        boolean hasTestError = !t.errorMessage().isEmpty() || !t.stackTrace().isEmpty();
        if (hasTestError) {
            sb.append("<div class=\"section\"><h4>Error</h4>");
            renderDiagnosis(sb, t.errorMessage(), t.stackTrace());
            if (!t.errorMessage().isEmpty()) {
                sb.append("<div class=\"error-block\">")
                  .append("<button type=\"button\" class=\"copy-btn\" aria-label=\"Copy\">Copy</button>")
                  .append("<pre>").append(esc(t.errorMessage())).append("</pre>")
                  .append("</div>");
            }
            if (!t.stackTrace().isEmpty()) {
                sb.append("<div class=\"error-block\">")
                  .append("<button type=\"button\" class=\"copy-btn\" aria-label=\"Copy\">Copy</button>")
                  .append("<pre>").append(esc(t.stackTrace())).append("</pre>")
                  .append("</div>");
            }
            sb.append("</div>\n");
        } else {
            // No test-level error — surface the first failing step's error at the
            // test level so the diagnosis banner is visible without expanding steps.
            for (Step s : t.steps()) {
                if (!s.errorMessage().isEmpty() || !s.stackTrace().isEmpty()) {
                    sb.append("<div class=\"section\">");
                    renderDiagnosis(sb, s.errorMessage(), s.stackTrace());
                    sb.append("</div>\n");
                    break;
                }
            }
        }

        sb.append("<div class=\"section\"><h4>Steps</h4>\n<div class=\"steps\">\n");
        for (Step s : t.steps()) renderStep(sb, s);
        sb.append("</div></div>\n");

        sb.append("</div></article>\n");
    }

    private void renderDiagnosis(StringBuilder sb, String message, String trace) {
        Diagnosis d = DIAGNOSIS.diagnose(message, trace);
        if (d == null) return;
        String sevClass = "diag-sev-" + d.severity().name().toLowerCase();
        sb.append("<div class=\"diag-banner ").append(sevClass).append("\">")
          .append("<div class=\"diag-icon\" aria-hidden=\"true\">&#9888;</div>")
          .append("<div class=\"diag-content\">")
          .append("<div class=\"diag-label\">").append(esc(d.label())).append("</div>")
          .append("<div class=\"diag-hint\">").append(esc(d.hint())).append("</div>")
          .append("<div class=\"diag-category\">").append(esc(d.category())).append("</div>")
          .append("</div></div>\n");
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
        if (millis < 60_000) return String.format(Locale.ROOT, "%.2f s", millis / 1000.0);
        long mins = millis / 60_000;
        double secs = (millis % 60_000) / 1000.0;
        return String.format(Locale.ROOT, "%dm %.1fs", mins, secs);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
