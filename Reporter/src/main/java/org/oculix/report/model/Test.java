package org.oculix.report.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One named test inside a run. Aggregates its steps, final outcome
 * (worst wins — any failed/error step marks the whole test), and timing.
 */
public final class Test {
    private final String name;
    private final Instant startedAt;
    private Instant endedAt;
    private Outcome outcome = Outcome.PASSED;
    private String errorMessage = "";
    private String stackTrace = "";
    private final List<Step> steps = new ArrayList<>();

    public Test(String name, Instant startedAt) {
        this.name = name;
        this.startedAt = startedAt;
    }

    public Test addStep(Step step) {
        steps.add(step);
        // Outcome resolution: "worst wins" among PASSED < SKIPPED < XFAILED/XPASSED < FAILED < ERROR.
        if (severity(step.outcome()) > severity(this.outcome)) {
            this.outcome = step.outcome();
        }
        return this;
    }

    public Test end(Instant endedAt) {
        this.endedAt = endedAt;
        return this;
    }

    public Test withError(String message, String stackTrace) {
        this.outcome = Outcome.ERROR;
        this.errorMessage = message == null ? "" : message;
        this.stackTrace = stackTrace == null ? "" : stackTrace;
        return this;
    }

    public String name() { return name; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public Outcome outcome() { return outcome; }
    public String errorMessage() { return errorMessage; }
    public String stackTrace() { return stackTrace; }
    public List<Step> steps() { return Collections.unmodifiableList(steps); }

    public Duration duration() {
        if (endedAt == null) return Duration.ZERO;
        return Duration.between(startedAt, endedAt);
    }

    private static int severity(Outcome o) {
        switch (o) {
            case PASSED:  return 0;
            case SKIPPED: return 1;
            case XPASSED: return 2;
            case XFAILED: return 3;
            case FAILED:  return 4;
            case ERROR:   return 5;
            default:      return 0;
        }
    }
}
