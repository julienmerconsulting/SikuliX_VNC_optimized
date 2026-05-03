package org.oculix.report.diagnosis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.oculix.report.diagnosis.Diagnosis.Severity.ERROR;
import static org.oculix.report.diagnosis.Diagnosis.Severity.WARNING;

/**
 * Catalogue of default rules. Order matters — first match wins, so put
 * the most specific patterns before the generic catch-alls. Categories
 * specific to OculiX (visual recognition) come first.
 */
public final class DiagnosisRules {

    public static final List<DiagnosisRule> DEFAULTS;

    static {
        List<DiagnosisRule> r = new ArrayList<>();

        // ---- OculiX / Sikuli visual recognition ----
        r.add(rule("FindFailed[\\s\\S]*?(?:cannot find|find failed)",
            "pattern_not_found", "Image pattern not found", ERROR,
            "OculiX could not locate the target image on screen. Re-capture the pattern, "
            + "lower the similarity threshold (e.g. 0.7), or extend the wait timeout."));
        r.add(rule("ImageMissing|cannot load image|image file not found",
            "image_missing", "Pattern image file missing", ERROR,
            "The image referenced by the test does not exist on disk. Check the image bundle path "
            + "and that the file was deployed with the test resources."));
        r.add(rule("UnsatisfiedLinkError[\\s\\S]*?opencv_java",
            "opencv_native", "OpenCV native library missing", ERROR,
            "OpenCV native binding failed to load. Ensure the OpenCV jar matches your OS/arch "
            + "(Windows x64, Linux x64, macOS) and that java.library.path is correctly set."));
        r.add(rule("HeadlessException|No X11 DISPLAY|cannot connect to X server",
            "display_headless", "No display available", ERROR,
            "Visual tests require a display server. On a CI runner, configure Xvfb or a virtual "
            + "framebuffer; on a server, connect via VNC or use a headed runner."));

        // ---- Selenium WebDriver ----
        r.add(rule("StaleElementReferenceException",
            "selenium_stale", "Stale element reference", WARNING,
            "The DOM was re-rendered between locating and using the element. Re-find the element "
            + "right before interacting, or wrap the action in a retry/until-click helper."));
        r.add(rule("ElementClickInterceptedException|element click intercepted",
            "selenium_intercepted", "Click intercepted by overlay", WARNING,
            "Another element (modal, banner, overlay) is covering the target. Wait for the overlay "
            + "to disappear or scroll the element into view first."));
        r.add(rule("ElementNotInteractableException|element not interactable",
            "selenium_not_interactable", "Element not interactable", WARNING,
            "The element exists in the DOM but is hidden, disabled, or off-screen. Check display, "
            + "visibility, and disabled state before interaction."));
        r.add(rule("NoSuchElementException|no such element|Unable to locate element",
            "selenium_element", "DOM element not found", ERROR,
            "The Selenium locator did not match any element. Verify the selector against the live "
            + "DOM and add an explicit wait if the element appears asynchronously."));
        r.add(rule("SessionNotCreatedException|session not created|chrome not reachable|browser exited",
            "selenium_session", "Browser session failed to start", ERROR,
            "The WebDriver could not start or lost the browser session. Check that the driver "
            + "binary version matches the installed browser."));
        r.add(rule("TimeoutException[\\s\\S]*?(?:expected condition|wait)",
            "selenium_timeout", "Selenium wait timed out", WARNING,
            "An explicit wait expired before the condition was met. Increase the timeout, or "
            + "reconsider whether the condition reflects what you actually need."));

        // ---- Test framework ----
        r.add(rule("ComparisonFailure|expected:?\\s*<.*?>\\s*but was:?\\s*<",
            "assertion_diff", "Assertion failed (expected vs. actual)", ERROR,
            "Expected and actual values differ. Compare the diff in the message; if the actual is "
            + "the new correct value, update the expectation."));
        r.add(rule("AssertionError|AssertionFailedError",
            "assertion", "Assertion failed", ERROR,
            "A test assertion failed. Inspect the message and re-run with the matching diagnostic "
            + "logs enabled to understand why the invariant broke."));

        // ---- Java common ----
        r.add(rule("NullPointerException",
            "null_pointer", "NullPointerException", ERROR,
            "A reference was null when used. Trace which variable is null at the indicated frame; "
            + "the message often names the field or the call (\"Cannot invoke … because \\\"x\\\" is null\")."));
        r.add(rule("ClassNotFoundException|NoClassDefFoundError",
            "class_loading", "Class not found at runtime", ERROR,
            "A required class was missing from the classpath. Check the dependency tree "
            + "(mvn dependency:tree) and packaging (shaded jar, scope=provided)."));
        r.add(rule("OutOfMemoryError",
            "out_of_memory", "Out of memory", ERROR,
            "The JVM ran out of heap. Increase -Xmx, reduce the working set, or take a heap dump "
            + "(-XX:+HeapDumpOnOutOfMemoryError) and analyse for leaks."));
        r.add(rule("ConcurrentModificationException",
            "concurrent_modification", "Concurrent modification", WARNING,
            "A collection was modified while being iterated. Use an iterator's remove(), copy the "
            + "collection before iterating, or switch to a concurrent collection."));

        // ---- I/O & permissions ----
        r.add(rule("FileNotFoundException|NoSuchFileException",
            "file_not_found", "File not found", ERROR,
            "The expected file did not exist at the given path. Check the path, working directory, "
            + "and whether the file is bundled in test resources."));
        r.add(rule("AccessDeniedException|java\\.nio\\.file\\.AccessDeniedException|Permission denied",
            "permissions", "Permission denied", ERROR,
            "The process lacks rights to read/write the target. Check file ownership, ACLs, or "
            + "whether the path lives under a protected directory."));

        // ---- Network ----
        r.add(rule("SocketTimeoutException|ConnectException|connection refused|connection reset|UnknownHostException",
            "network", "Network failure", WARNING,
            "A network call failed (timeout, refused, host unknown). Check the target service, "
            + "DNS resolution, and proxy settings; consider retries with backoff."));

        // ---- Generic catch-alls ----
        r.add(rule("(?i)\\btime[d]?\\s?out\\b|TimeoutException",
            "timeout", "Operation timed out", WARNING,
            "Something took longer than its allotted time. Increase the timeout if the operation "
            + "is genuinely slow, otherwise investigate why it is no longer responsive."));

        DEFAULTS = Collections.unmodifiableList(r);
    }

    private static DiagnosisRule rule(String regex, String category, String label,
                                      Diagnosis.Severity severity, String hint) {
        return new DiagnosisRule(Pattern.compile(regex), category, label, severity, hint);
    }

    private DiagnosisRules() {}
}
