package org.oculix.report.selenium;

import org.oculix.report.OculixReporter;
import org.oculix.report.model.Outcome;
import org.oculix.report.model.Step;
import org.oculix.report.model.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverEventListener;
import org.openqa.selenium.support.events.WebDriverListener;

import java.time.Instant;

/**
 * Single listener implementing BOTH Selenium 3 ({@link WebDriverEventListener},
 * deprecated but still present in Selenium 4.0–4.14) and Selenium 4
 * ({@link WebDriverListener}) event interfaces.
 *
 * <p>Why one class? The two interfaces share no method names — so a single
 * instance can satisfy both contracts without collision. That lets
 * {@link SeleniumWrap} pick the right Selenium version at runtime without
 * needing two different listener classes to load.
 *
 * <p>Each captured action becomes a {@link Step} on the current
 * {@link Test} in {@link OculixReporter}. If no test is active (listener
 * fired outside a test), the event is silently dropped — no exception, no
 * ghost steps in the run.
 */
@SuppressWarnings("deprecation")
public final class SeleniumReportingListener implements WebDriverListener, WebDriverEventListener {

    // ========== Selenium 4 API (WebDriverListener) ==========

    @Override
    public void beforeClick(WebElement element) {
        startStep("selenium.click", describe(element));
    }

    @Override
    public void afterClick(WebElement element) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void beforeSendKeys(WebElement element, CharSequence... keysToSend) {
        startStep("selenium.type", describe(element) + " ← " + truncate(String.valueOf(String.join("", keysToSend))));
    }

    @Override
    public void afterSendKeys(WebElement element, CharSequence... keysToSend) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void beforeGet(WebDriver driver, String url) {
        startStep("selenium.get", url);
    }

    @Override
    public void afterGet(WebDriver driver, String url) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void beforeAnyWebDriverCall(WebDriver driver, java.lang.reflect.Method method, Object[] args) {
        // Hook for "navigate", "close", "quit", "findElement" etc.
        // We rely on more specific hooks above where they exist.
    }

    // ========== Selenium 3 API (WebDriverEventListener) ==========

    @Override
    public void beforeClickOn(WebElement element, WebDriver driver) {
        startStep("selenium.click", describe(element));
    }

    @Override
    public void afterClickOn(WebElement element, WebDriver driver) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void beforeChangeValueOf(WebElement element, WebDriver driver, CharSequence[] keysToSend) {
        String val = keysToSend == null ? "" : String.join("", keysToSend);
        startStep("selenium.type", describe(element) + " ← " + truncate(val));
    }

    @Override
    public void afterChangeValueOf(WebElement element, WebDriver driver, CharSequence[] keysToSend) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void beforeNavigateTo(String url, WebDriver driver) {
        startStep("selenium.get", url);
    }

    @Override
    public void afterNavigateTo(String url, WebDriver driver) {
        endStep(Outcome.PASSED, null, null);
    }

    @Override
    public void onException(Throwable throwable, WebDriver driver) {
        endStep(Outcome.ERROR, throwable.getMessage(), stack(throwable));
    }

    // Selenium 3 interface has many other methods that we leave as no-ops
    // (inherited from the default interface methods where present, or
    // explicitly empty where abstract).
    @Override public void beforeAlertAccept(WebDriver driver) {}
    @Override public void afterAlertAccept(WebDriver driver) {}
    @Override public void afterAlertDismiss(WebDriver driver) {}
    @Override public void beforeAlertDismiss(WebDriver driver) {}
    @Override public void beforeNavigateBack(WebDriver driver) {}
    @Override public void afterNavigateBack(WebDriver driver) {}
    @Override public void beforeNavigateForward(WebDriver driver) {}
    @Override public void afterNavigateForward(WebDriver driver) {}
    @Override public void beforeNavigateRefresh(WebDriver driver) {}
    @Override public void afterNavigateRefresh(WebDriver driver) {}
    @Override public void beforeFindBy(org.openqa.selenium.By by, WebElement element, WebDriver driver) {}
    @Override public void afterFindBy(org.openqa.selenium.By by, WebElement element, WebDriver driver) {}
    @Override public <X> void beforeGetScreenshotAs(org.openqa.selenium.OutputType<X> target) {}
    @Override public <X> void afterGetScreenshotAs(org.openqa.selenium.OutputType<X> target, X screenshot) {}
    @Override public void beforeGetText(WebElement element, WebDriver driver) {}
    @Override public void afterGetText(WebElement element, WebDriver driver, String text) {}
    @Override public void beforeScript(String script, WebDriver driver) {}
    @Override public void afterScript(String script, WebDriver driver) {}
    @Override public void beforeSwitchToWindow(String windowName, WebDriver driver) {}
    @Override public void afterSwitchToWindow(String windowName, WebDriver driver) {}

    // ========== Internals ==========

    private Step pending;

    private synchronized void startStep(String action, String target) {
        Test t = OculixReporter.currentTest();
        if (t == null) { pending = null; return; }
        pending = new Step(action, target, Instant.now());
    }

    private synchronized void endStep(Outcome outcome, String msg, String st) {
        Test t = OculixReporter.currentTest();
        if (t == null || pending == null) { pending = null; return; }
        pending.end(Instant.now(), outcome);
        if (outcome != Outcome.PASSED) pending.withError(msg, st);
        t.addStep(pending);
        pending = null;
    }

    private static String describe(WebElement el) {
        if (el == null) return "";
        try { return el.toString(); } catch (Exception e) { return "<stale element>"; }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= 40) return s;
        return s.substring(0, 40) + "...";
    }

    private static String stack(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) sb.append("    at ").append(e).append('\n');
        return sb.toString();
    }
}
