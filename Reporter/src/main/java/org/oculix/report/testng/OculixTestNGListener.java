package org.oculix.report.testng;

import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.file.Path;

/**
 * TestNG integration. Add {@code @Listeners(OculixTestNGListener.class)}
 * to your test class and the reporter activates automatically:
 *
 * <ul>
 *   <li>A {@code TestRun} is opened at the start of the suite.</li>
 *   <li>A {@code Test} is opened at the start of each TestNG method and
 *     closed with the matching outcome (PASSED / FAILED / SKIPPED).</li>
 *   <li>At the end of the suite, the HTML report is written to
 *     {@code target/oculix-report.html} (override via system property
 *     {@code oculix.report.out}).</li>
 * </ul>
 *
 * <p>Your test methods only need a {@code ReportedScreen} (or any Screen
 * obtained via {@link OculixReporter#wrapScreen()}) for OculiX actions,
 * and a {@code WebDriver} obtained via {@link OculixReporter#wrapDriver}
 * for Selenium actions. All Steps land on the correct {@code Test}
 * automatically thanks to the {@code ThreadLocal} in {@code OculixReporter}.
 */
public class OculixTestNGListener implements ITestListener, ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        OculixReporter.startSuite(suite.getName() == null ? "TestNG Suite" : suite.getName());
    }

    @Override
    public void onStart(ITestContext context) {
        // Called once per <test> element inside the suite. If startSuite
        // was not called (no ISuite listener registration), fall back
        // here so we still produce a report.
        if (OculixReporter.currentRun() == null) {
            OculixReporter.startSuite(context.getSuite().getName());
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        OculixReporter.startTest(result.getMethod().getQualifiedName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        OculixReporter.endTest(Outcome.PASSED, null, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable t = result.getThrowable();
        OculixReporter.endTest(Outcome.FAILED,
            t == null ? "" : t.getMessage(),
            t == null ? "" : stackOf(t));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        OculixReporter.endTest(Outcome.SKIPPED, null, null);
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        // Treated as passed for reporting purposes.
        OculixReporter.endTest(Outcome.PASSED, null, null);
    }

    @Override
    public void onFinish(ISuite suite) {
        OculixReporter.endSuite();
        try {
            OculixReporter.writeTo(Path.of(
                System.getProperty("oculix.report.out", "target/oculix-report.html")));
        } catch (IOException e) {
            System.err.println("OculiX reporter: failed to write HTML report: " + e.getMessage());
        }
    }

    @Override
    public void onFinish(ITestContext context) {
        // No-op here; writing happens in onFinish(ISuite).
    }

    private static String stackOf(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) sb.append("    at ").append(e).append('\n');
        return sb.toString();
    }
}
