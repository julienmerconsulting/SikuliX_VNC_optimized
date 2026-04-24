package org.oculix.report;

import org.oculix.report.model.Outcome;
import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;
import org.oculix.report.render.HtmlRenderer;
import org.sikuli.script.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Static facade — the single entry point users interact with.
 *
 * <p>Keeps a {@link TestRun} and a {@link ThreadLocal}&lt;{@link Test}&gt; so
 * wrapped Screens and WebDrivers can attach their captured steps to the
 * test currently executing without having to pass the Test reference around.
 *
 * <p>Typical flow (handled automatically by TestNG / JUnit 5 listeners):
 * <pre>
 *   OculixReporter.startSuite("My Regression");
 *   OculixReporter.startTest("loginTest");
 *   // ... test runs, ReportedScreen and Selenium listener push steps ...
 *   OculixReporter.endTest(Outcome.PASSED, null, null);
 *   OculixReporter.endSuite();
 *   OculixReporter.writeTo(Path.of("target/report.html"));
 * </pre>
 *
 * <p>Thread-safety: the run is shared across threads (tests may run in
 * parallel via TestNG {@code parallel="methods"} or JUnit 5 concurrent
 * execution), but the <i>current</i> {@link Test} is per-thread, so events
 * land on the right test even with parallel execution.
 */
public final class OculixReporter {

    private static volatile TestRun currentRun;
    private static final ThreadLocal<Test> currentTest = new ThreadLocal<>();

    private OculixReporter() {}

    // ---- Suite lifecycle ----

    public static synchronized TestRun startSuite(String title) {
        currentRun = new TestRun(title, Instant.now());
        return currentRun;
    }

    public static synchronized TestRun endSuite() {
        if (currentRun != null) currentRun.end(Instant.now());
        return currentRun;
    }

    public static TestRun currentRun() {
        return currentRun;
    }

    // ---- Test lifecycle ----

    public static synchronized Test startTest(String name) {
        if (currentRun == null) startSuite("OculiX Suite");
        Test t = new Test(name, Instant.now());
        currentRun.addTest(t);
        currentTest.set(t);
        return t;
    }

    public static Test endTest(Outcome outcome, String errorMessage, String stackTrace) {
        Test t = currentTest.get();
        if (t == null) return null;
        t.end(Instant.now());
        if (outcome == Outcome.ERROR || outcome == Outcome.FAILED) {
            t.withError(errorMessage == null ? "" : errorMessage,
                stackTrace == null ? "" : stackTrace);
        }
        currentTest.remove();
        return t;
    }

    public static Test endTest() {
        return endTest(Outcome.PASSED, null, null);
    }

    public static Test currentTest() {
        return currentTest.get();
    }

    // ---- Output ----

    public static void writeTo(Path path) throws IOException {
        if (currentRun == null) return;
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        new HtmlRenderer().renderTo(currentRun, path);
    }

    // ---- Wrappers — syntactic sugar ----

    /**
     * Returns a {@link ReportedScreen} bound to the current thread's test.
     * Safe to call before any test has started — the returned Screen just
     * won't record anything until a test context exists.
     */
    public static Screen wrapScreen() {
        return new ReportedScreen();
    }

    /**
     * Instruments a {@code WebDriver} so every Selenium action emits a Step
     * on the current test. Implementation lives in
     * {@code org.oculix.report.selenium.SeleniumWrap} (reflectively loaded
     * so Selenium stays an optional dependency — no ClassNotFoundError for
     * users who don't use Selenium).
     */
    public static Object wrapDriver(Object driver) {
        try {
            Class<?> wrap = Class.forName("org.oculix.report.selenium.SeleniumWrap");
            return wrap.getMethod("wrap", Object.class).invoke(null, driver);
        } catch (ClassNotFoundException cnf) {
            throw new IllegalStateException(
                "Selenium integration not on classpath. Add selenium-java >= 4.0 to use wrapDriver.", cnf);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to wrap WebDriver: " + e.getMessage(), e);
        }
    }
}
