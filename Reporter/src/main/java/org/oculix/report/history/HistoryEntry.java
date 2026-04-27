package org.oculix.report.history;

import org.oculix.report.model.Outcome;
import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single past run snapshot — id, timestamp, and the outcome each test
 * had in that run. Built from a {@link TestRun} after it ends.
 */
public final class HistoryEntry {

    private final String runId;
    private final long timestampEpochMs;
    private final Map<String, Outcome> outcomes;

    public HistoryEntry(String runId, long timestampEpochMs, Map<String, Outcome> outcomes) {
        this.runId = runId;
        this.timestampEpochMs = timestampEpochMs;
        this.outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
    }

    public static HistoryEntry of(TestRun run) {
        Map<String, Outcome> map = new LinkedHashMap<>();
        for (Test t : run.tests()) map.put(t.name(), t.outcome());
        return new HistoryEntry(run.startedAt().toString(), run.startedAt().toEpochMilli(), map);
    }

    public String runId()              { return runId; }
    public long timestampEpochMs()     { return timestampEpochMs; }
    public Map<String, Outcome> outcomes() { return outcomes; }
}
