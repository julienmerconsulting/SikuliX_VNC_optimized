package org.oculix.report.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single action inside a test: {@code click}, {@code type}, {@code wait}, etc.
 * Holds the action name, its target, timing, outcome, and zero-to-many screenshots
 * (typically one before and one after the action).
 */
public final class Step {
    private final String action;
    private final String target;
    private final Instant startedAt;
    private Instant endedAt;
    private Outcome outcome = Outcome.PASSED;
    private String errorMessage = "";
    private String stackTrace = "";
    private final List<Screenshot> screenshots = new ArrayList<>();

    public Step(String action, String target, Instant startedAt) {
        this.action = action;
        this.target = target;
        this.startedAt = startedAt;
    }

    public Step end(Instant endedAt, Outcome outcome) {
        this.endedAt = endedAt;
        this.outcome = outcome;
        return this;
    }

    public Step withError(String message, String stackTrace) {
        this.errorMessage = message == null ? "" : message;
        this.stackTrace = stackTrace == null ? "" : stackTrace;
        return this;
    }

    public Step addScreenshot(Screenshot shot) {
        screenshots.add(shot);
        return this;
    }

    public String action() { return action; }
    public String target() { return target; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public Outcome outcome() { return outcome; }
    public String errorMessage() { return errorMessage; }
    public String stackTrace() { return stackTrace; }
    public List<Screenshot> screenshots() { return Collections.unmodifiableList(screenshots); }

    public Duration duration() {
        if (endedAt == null) return Duration.ZERO;
        return Duration.between(startedAt, endedAt);
    }
}
