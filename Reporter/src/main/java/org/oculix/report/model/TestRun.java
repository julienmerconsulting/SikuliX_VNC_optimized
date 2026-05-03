package org.oculix.report.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A test suite run. Top-level container the HtmlRenderer consumes to produce
 * the single-file HTML report.
 */
public final class TestRun {
    private final String title;
    private final Instant startedAt;
    private Instant endedAt;
    private final List<Test> tests = new ArrayList<>();
    private final String environment;
    private final String oculixVersion;

    public TestRun(String title, Instant startedAt, String environment, String oculixVersion) {
        this.title = title;
        this.startedAt = startedAt;
        this.environment = environment == null ? "" : environment;
        this.oculixVersion = oculixVersion == null ? "" : oculixVersion;
    }

    public TestRun(String title, Instant startedAt) {
        this(title, startedAt,
            System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " / Java " + System.getProperty("java.version"),
            "dev");
    }

    public TestRun addTest(Test test) {
        tests.add(test);
        return this;
    }

    public TestRun end(Instant endedAt) {
        this.endedAt = endedAt;
        return this;
    }

    public String title() { return title; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public List<Test> tests() { return Collections.unmodifiableList(tests); }
    public String environment() { return environment; }
    public String oculixVersion() { return oculixVersion; }

    public Duration duration() {
        if (endedAt == null) return Duration.ZERO;
        return Duration.between(startedAt, endedAt);
    }

    public int total() { return tests.size(); }

    public Map<Outcome, Integer> counts() {
        EnumMap<Outcome, Integer> out = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) out.put(o, 0);
        for (Test t : tests) out.merge(t.outcome(), 1, Integer::sum);
        return out;
    }

    public double successRate() {
        if (tests.isEmpty()) return 0.0;
        int ok = counts().get(Outcome.PASSED) + counts().get(Outcome.XPASSED);
        return 100.0 * ok / tests.size();
    }
}
