package org.oculix.report;

import org.oculix.report.model.Outcome;
import org.oculix.report.model.Screenshot;
import org.oculix.report.model.Step;
import org.oculix.report.model.Test;
import org.sikuli.script.FindFailed;
import org.sikuli.script.Match;
import org.sikuli.script.Pattern;
import org.sikuli.script.Region;
import org.sikuli.script.Screen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Drop-in wrapper around {@link Screen}. Every public action you care about
 * ({@code click}, {@code type}, {@code wait}, {@code find}, {@code exists},
 * {@code dragDrop}) is intercepted: a screenshot is captured before, the
 * action is delegated to the underlying {@link Screen}, a screenshot is
 * captured after, and a {@link Step} is appended to the current {@link Test}.
 *
 * <p><b>Zero impact on existing code.</b> If a caller uses a plain
 * {@link Screen}, nothing changes. Only tests that explicitly opt in by
 * constructing {@code new ReportedScreen(screen, currentTest)} will emit
 * reporting events.
 *
 * <p>This class does not modify any existing OculiX API class. It extends
 * {@link Screen} and overrides only the subset of methods relevant for
 * reporting. All other methods fall through to {@link Screen}'s original
 * behavior.
 */
public class ReportedScreen extends Screen {

    // When null → read from OculixReporter.currentTest() on each call
    // (ambient mode, used by TestNG / JUnit listeners). When non-null →
    // emit steps to this specific test (explicit mode, pre-Phase-5 usage).
    private final Test explicitTest;

    /**
     * Ambient mode. The current {@link Test} is looked up from
     * {@link org.oculix.report.OculixReporter#currentTest()} at each
     * action — meaning this instance follows the per-thread test context
     * set by listeners like {@code OculixTestNGListener} and
     * {@code OculixJUnit5Extension}. Events fired outside a test window
     * are silently dropped, no exception.
     */
    public ReportedScreen() {
        super();
        this.explicitTest = null;
    }

    /**
     * Explicit mode. All actions on this instance emit steps to the
     * given test regardless of any thread-local context.
     */
    public ReportedScreen(Test test) {
        super();
        this.explicitTest = test;
    }

    private Test currentTest() {
        if (explicitTest != null) return explicitTest;
        return org.oculix.report.OculixReporter.currentTest();
    }

    // ---- click ----

    @Override
    public int click(Object target) throws FindFailed {
        return (int) traced("click", target, () -> super.click(target));
    }

    @Override
    public int click(Object target, Integer modifiers) throws FindFailed {
        return (int) traced("click", target, () -> super.click(target, modifiers));
    }

    // ---- doubleClick ----

    @Override
    public int doubleClick(Object target) throws FindFailed {
        return (int) traced("doubleClick", target, () -> super.doubleClick(target));
    }

    @Override
    public int doubleClick(Object target, Integer modifiers) throws FindFailed {
        return (int) traced("doubleClick", target, () -> super.doubleClick(target, modifiers));
    }

    // ---- rightClick ----

    @Override
    public int rightClick(Object target) throws FindFailed {
        return (int) traced("rightClick", target, () -> super.rightClick(target));
    }

    @Override
    public int rightClick(Object target, Integer modifiers) throws FindFailed {
        return (int) traced("rightClick", target, () -> super.rightClick(target, modifiers));
    }

    // ---- type / paste ----

    @Override
    public int type(String text) {
        try {
            return (int) traced("type", text, () -> super.type(text));
        } catch (FindFailed e) {
            return 0; // type(String) never throws FindFailed, but lambda requires it
        }
    }

    @Override
    public int paste(String text) {
        try {
            return (int) traced("paste", text, () -> super.paste(text));
        } catch (FindFailed e) {
            return 0;
        }
    }

    // ---- find / wait / exists ----

    @Override
    public <PSI> Match find(PSI target) throws FindFailed {
        return (Match) traced("find", target, () -> super.find(target));
    }

    @Override
    public <PSI> Match wait(PSI target) throws FindFailed {
        return (Match) traced("wait", target, () -> super.wait(target));
    }

    @Override
    public <PSI> Match wait(PSI target, double timeout) throws FindFailed {
        return (Match) traced("wait", target, () -> super.wait(target, timeout));
    }

    @Override
    public <PSI> Match exists(PSI target) {
        try {
            return (Match) traced("exists", target, () -> super.exists(target));
        } catch (FindFailed e) {
            return null;
        }
    }

    // ---- dragDrop ----

    @Override
    public <PFRML> int dragDrop(PFRML from, PFRML to) throws FindFailed {
        return (int) traced("dragDrop", from + " → " + to,
            () -> super.dragDrop(from, to));
    }

    // ---- Core tracing logic ----

    @FunctionalInterface
    private interface Action {
        Object invoke() throws FindFailed;
    }

    private Object traced(String action, Object target, Action body) throws FindFailed {
        Test test = currentTest();
        // If no test context is active, just delegate without tracing —
        // prevents NPE when a user's code runs before any listener has
        // opened a test.
        if (test == null) return body.invoke();

        Instant start = Instant.now();
        Step step = new Step(action, describeTarget(target), start);
        Screenshot before = captureSafe("before — " + action);
        if (before != null) step.addScreenshot(before);

        try {
            Object result = body.invoke();
            Instant end = Instant.now();
            step.end(end, Outcome.PASSED);
            Screenshot after = captureSafe("after — " + action);
            if (after != null) step.addScreenshot(after);
            test.addStep(step);
            return result;
        } catch (FindFailed ff) {
            Instant end = Instant.now();
            step.end(end, Outcome.FAILED).withError(ff.getMessage(), stackOf(ff));
            Screenshot failShot = captureSafe("failure");
            if (failShot != null) step.addScreenshot(failShot);
            test.addStep(step);
            throw ff;
        } catch (RuntimeException re) {
            Instant end = Instant.now();
            step.end(end, Outcome.ERROR).withError(re.getMessage(), stackOf(re));
            Screenshot failShot = captureSafe("error");
            if (failShot != null) step.addScreenshot(failShot);
            test.addStep(step);
            throw re;
        }
    }

    private String describeTarget(Object target) {
        if (target == null) return "";
        if (target instanceof Pattern)  return ((Pattern) target).toString();
        if (target instanceof Region)   return ((Region) target).toStringShort();
        if (target instanceof String)   return (String) target;
        return target.toString();
    }

    private Screenshot captureSafe(String caption) {
        try {
            BufferedImage img = super.capture().getImage();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            return new Screenshot("data:image/png;base64," + b64, Instant.now(), caption,
                img.getWidth(), img.getHeight());
        } catch (IOException | RuntimeException e) {
            return null; // never let screenshot failure break the test
        }
    }

    private static String stackOf(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) sb.append("    at ").append(e).append('\n');
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static Duration since(Instant i) { return Duration.between(i, Instant.now()); }
}
