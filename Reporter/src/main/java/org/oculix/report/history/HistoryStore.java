package org.oculix.report.history;

import org.oculix.report.model.Outcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Persists the last N {@link HistoryEntry}s to a JSON file with a tiny
 * hand-rolled serializer/parser — no Jackson, zero external dependency.
 * Robust to corruption: a malformed file resets the store on next write.
 */
public final class HistoryStore {

    public static final int MAX_RUNS = 20;

    private final List<HistoryEntry> entries = new ArrayList<>();

    public List<HistoryEntry> entries() { return Collections.unmodifiableList(entries); }
    public boolean isEmpty()            { return entries.isEmpty(); }
    public int size()                   { return entries.size(); }

    public void append(HistoryEntry e) {
        entries.add(e);
        while (entries.size() > MAX_RUNS) entries.remove(0);
    }

    // ---- I/O ----

    public static HistoryStore loadFrom(Path file) {
        HistoryStore s = new HistoryStore();
        if (file == null || !Files.exists(file)) return s;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            parseInto(content, s);
        } catch (IOException | RuntimeException ignored) {
            // unreadable / corrupt — start fresh, do not throw
        }
        return s;
    }

    public void saveTo(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, toJson(), StandardCharsets.UTF_8);
    }

    // ---- Serialize ----

    String toJson() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"v\":1,\"runs\":[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(',');
            HistoryEntry e = entries.get(i);
            sb.append("\n  {\"id\":");
            jsonString(sb, e.runId());
            sb.append(",\"ts\":").append(e.timestampEpochMs());
            sb.append(",\"tests\":{");
            int j = 0;
            for (Map.Entry<String, Outcome> en : e.outcomes().entrySet()) {
                if (j++ > 0) sb.append(',');
                jsonString(sb, en.getKey());
                sb.append(':');
                jsonString(sb, en.getValue().slug());
            }
            sb.append("}}");
        }
        sb.append("\n]}\n");
        return sb.toString();
    }

    // ---- Parse ----

    private static void parseInto(String json, HistoryStore store) {
        int i = json.indexOf("\"runs\":[");
        if (i < 0) return;
        i += 8;
        while (i < json.length()) {
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) i++;
            if (i >= json.length() || json.charAt(i) == ']') break;
            if (json.charAt(i) != '{') break;
            int end = matchingBrace(json, i);
            if (end < 0) break;
            HistoryEntry e = parseRun(json.substring(i, end + 1));
            if (e != null) store.entries.add(e);
            i = end + 1;
        }
    }

    private static HistoryEntry parseRun(String run) {
        String id = extractString(run, "\"id\":");
        Long ts = extractNumber(run, "\"ts\":");
        if (id == null || ts == null) return null;
        int tStart = run.indexOf("\"tests\":");
        if (tStart < 0) return null;
        int braceOpen = run.indexOf('{', tStart);
        if (braceOpen < 0) return null;
        int braceClose = matchingBrace(run, braceOpen);
        if (braceClose < 0) return null;
        String testsBody = run.substring(braceOpen + 1, braceClose);
        Map<String, Outcome> map = new LinkedHashMap<>();
        int p = 0;
        while (p < testsBody.length()) {
            while (p < testsBody.length() && (Character.isWhitespace(testsBody.charAt(p)) || testsBody.charAt(p) == ',')) p++;
            if (p >= testsBody.length() || testsBody.charAt(p) != '"') break;
            int keyEnd = findStringEnd(testsBody, p);
            if (keyEnd < 0) break;
            String key = unescape(testsBody.substring(p + 1, keyEnd));
            p = keyEnd + 1;
            while (p < testsBody.length() && Character.isWhitespace(testsBody.charAt(p))) p++;
            if (p >= testsBody.length() || testsBody.charAt(p) != ':') break;
            p++;
            while (p < testsBody.length() && Character.isWhitespace(testsBody.charAt(p))) p++;
            if (p >= testsBody.length() || testsBody.charAt(p) != '"') break;
            int valEnd = findStringEnd(testsBody, p);
            if (valEnd < 0) break;
            String val = unescape(testsBody.substring(p + 1, valEnd));
            p = valEnd + 1;
            try {
                map.put(key, Outcome.valueOf(val.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) { /* unknown outcome — skip */ }
        }
        return new HistoryEntry(id, ts, map);
    }

    private static int matchingBrace(String s, int start) {
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (inStr) {
                if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int findStringEnd(String s, int start) {
        boolean esc = false;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\') { esc = true; continue; }
            if (c == '"') return i;
        }
        return -1;
    }

    private static String extractString(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        i = s.indexOf('"', i + key.length());
        if (i < 0) return null;
        int e = findStringEnd(s, i);
        if (e < 0) return null;
        return unescape(s.substring(i + 1, e));
    }

    private static Long extractNumber(String s, String key) {
        int i = s.indexOf(key);
        if (i < 0) return null;
        i += key.length();
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        int start = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-')) i++;
        if (i == start) return null;
        try { return Long.parseLong(s.substring(start, i)); } catch (NumberFormatException e) { return null; }
    }

    private static void jsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case '"':  sb.append('"');  i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n':  sb.append('\n'); i++; break;
                    case 'r':  sb.append('\r'); i++; break;
                    case 't':  sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                                i += 5;
                            } catch (NumberFormatException e) { sb.append(c); }
                        } else sb.append(c);
                        break;
                    default: sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
