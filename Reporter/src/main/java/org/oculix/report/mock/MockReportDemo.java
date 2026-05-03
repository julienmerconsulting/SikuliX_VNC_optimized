package org.oculix.report.mock;

import org.oculix.report.history.HistoryEntry;
import org.oculix.report.history.HistoryStore;
import org.oculix.report.model.Outcome;
import org.oculix.report.model.Screenshot;
import org.oculix.report.model.Step;
import org.oculix.report.model.Test;
import org.oculix.report.model.TestRun;
import org.oculix.report.render.HtmlRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Standalone demo: builds a realistic fake {@link TestRun} with
 * programmatically generated screenshots (via {@link Graphics2D}), renders
 * the HTML, and writes it to {@code target/demo-report.html}.
 *
 * <p>Purpose: visual QA of the reporter without needing a live OculiX
 * session. Run with:
 * <pre>mvn -pl Reporter exec:java</pre>
 * Then open {@code Reporter/target/demo-report.html} in a browser.
 */
public final class MockReportDemo {

    private static final Random RNG = new Random(42);
    private static final int SHOT_W = 800;
    private static final int SHOT_H = 500;

    public static void main(String[] args) throws IOException {
        Instant t0 = Instant.now().minus(Duration.ofMinutes(2));
        TestRun run = new TestRun("Checkout flow — e-commerce regression", t0);

        run.addTest(buildPassedTest("LoginTest.valid_credentials", t0.plusSeconds(1), 3));
        run.addTest(buildPassedTest("LoginTest.remember_me_checkbox", t0.plusSeconds(8), 4));
        run.addTest(buildPassedTest("CartTest.add_single_product", t0.plusSeconds(15), 5));
        run.addTest(buildFailedTest("CartTest.add_out_of_stock_product", t0.plusSeconds(24)));
        run.addTest(buildPassedTest("CheckoutTest.guest_checkout", t0.plusSeconds(34), 6));
        run.addTest(buildErrorTest("CheckoutTest.apply_promo_code", t0.plusSeconds(48)));
        run.addTest(buildSkipped("CheckoutTest.apple_pay", t0.plusSeconds(58)));
        run.addTest(buildPassedTest("OrderTest.history_view", t0.plusSeconds(62), 3));
        run.addTest(buildPassedTest("OrderTest.reorder_previous", t0.plusSeconds(70), 4));

        run.end(t0.plusSeconds(82));

        Path out = Path.of("target", "demo-report.html");
        Files.createDirectories(out.getParent());
        new HtmlRenderer().withHistory(buildFakeHistory(t0)).renderTo(run, out);
        System.out.println("Report written to: " + out.toAbsolutePath());
        System.out.println("Open it in a browser to preview.");
    }

    /**
     * Synthesizes 6 prior runs to make sparkline + flaky badges + trends
     * visible in the demo. Tests in the current run that exist in history
     * get a sparkline; one test ({@code CheckoutTest.apply_promo_code}) is
     * intentionally flippy across runs to trigger the flaky badge.
     */
    private static HistoryStore buildFakeHistory(Instant currentRunStart) {
        HistoryStore store = new HistoryStore();
        String[] names = {
            "LoginTest.valid_credentials",
            "LoginTest.remember_me_checkbox",
            "CartTest.add_single_product",
            "CartTest.add_out_of_stock_product",
            "CheckoutTest.guest_checkout",
            "CheckoutTest.apply_promo_code",
            "CheckoutTest.apple_pay",
            "OrderTest.history_view",
            "OrderTest.reorder_previous",
        };
        // Past run patterns — most are stable PASSED, the last two columns simulate
        // the regressions/flakiness that should surface in the trends bar.
        Outcome[][] past = {
            // run -6: everything green
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.PASSED, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
            // run -5: promo_code starts flipping
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.FAILED, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
            // run -4
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.PASSED, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
            // run -3: promo_code error, out_of_stock flaky starts
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.ERROR, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
            // run -2
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.PASSED, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
            // run -1 (previous run): out_of_stock was passing, will regress this run → 1 regression
            { Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED, Outcome.PASSED,
              Outcome.PASSED, Outcome.SKIPPED, Outcome.PASSED, Outcome.PASSED },
        };
        for (int i = 0; i < past.length; i++) {
            Map<String, Outcome> m = new LinkedHashMap<>();
            for (int j = 0; j < names.length; j++) m.put(names[j], past[i][j]);
            Instant ts = currentRunStart.minus(Duration.ofDays(past.length - i));
            store.append(new HistoryEntry(ts.toString(), ts.toEpochMilli(), m));
        }
        return store;
    }

    // ---- Test builders ----

    private static Test buildPassedTest(String name, Instant start, int stepCount) {
        Test t = new Test(name, start);
        Instant cur = start;
        String[] actions = {"click", "type", "wait", "find", "click", "assert"};
        String[] targets = {
            "login-form.png", "admin", "dashboard.png", "cart-icon.png",
            "add-to-cart.png", "checkout-button.png", "confirmation.png"
        };
        for (int i = 0; i < stepCount; i++) {
            String action = actions[i % actions.length];
            String target = targets[RNG.nextInt(targets.length)];
            Instant stepStart = cur;
            Instant stepEnd = stepStart.plus(randomDuration(200, 1400));
            Step s = new Step(action, target, stepStart).end(stepEnd, Outcome.PASSED);
            s.addScreenshot(fakeShot("before — " + action, Color.decode("#f1f5f9")));
            s.addScreenshot(fakeShot("after — " + action, Color.decode("#dcfce7")));
            t.addStep(s);
            cur = stepEnd;
        }
        t.end(cur);
        return t;
    }

    private static Test buildFailedTest(String name, Instant start) {
        Test t = new Test(name, start);
        Instant cur = start;
        // 2 passed steps then a failed one
        for (int i = 0; i < 2; i++) {
            Instant ss = cur;
            Instant se = ss.plus(randomDuration(300, 900));
            Step s = new Step("click", "product-xyz.png", ss).end(se, Outcome.PASSED);
            s.addScreenshot(fakeShot("step " + (i + 1), Color.decode("#e0e7ff")));
            t.addStep(s);
            cur = se;
        }
        Instant ss = cur;
        Instant se = ss.plus(randomDuration(2000, 5000));
        Step failed = new Step("wait", "add-to-cart-enabled.png", ss).end(se, Outcome.FAILED)
            .withError(
                "FindFailed: cannot find \"add-to-cart-enabled.png\" after 5.0s (min similarity 0.70)",
                "at org.sikuli.script.Region.wait(Region.java:1234)\n"
              + "at com.shop.tests.CartTest.add_out_of_stock_product(CartTest.java:87)"
            );
        failed.addScreenshot(fakeShot("screen at timeout", Color.decode("#fee2e2")));
        t.addStep(failed);
        t.end(se);
        return t;
    }

    private static Test buildErrorTest(String name, Instant start) {
        Test t = new Test(name, start);
        Instant end = start.plus(randomDuration(500, 1200));
        t.withError(
            "NullPointerException in promo-code validator",
            "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"code\" is null\n"
          + "at com.shop.checkout.PromoCodeValidator.validate(PromoCodeValidator.java:42)\n"
          + "at com.shop.tests.CheckoutTest.apply_promo_code(CheckoutTest.java:156)"
        );
        Step s = new Step("type", "promo-code-input.png", start).end(end, Outcome.ERROR);
        s.addScreenshot(fakeShot("error context", Color.decode("#ffedd5")));
        t.addStep(s);
        t.end(end);
        return t;
    }

    private static Test buildSkipped(String name, Instant start) {
        Test t = new Test(name, start);
        Instant end = start.plus(randomDuration(50, 200));
        Step s = new Step("skip", "apple-pay only on macOS", start).end(end, Outcome.SKIPPED);
        t.addStep(s);
        t.end(end);
        return t;
    }

    // ---- Fake screenshot generator ----

    private static Screenshot fakeShot(String caption, Color bg) {
        BufferedImage img = new BufferedImage(SHOT_W, SHOT_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Gradient background
        GradientPaint gp = new GradientPaint(0, 0, bg, SHOT_W, SHOT_H, bg.darker());
        g.setPaint(gp);
        g.fillRect(0, 0, SHOT_W, SHOT_H);
        // Mock browser chrome
        g.setColor(new Color(0xe5, 0xe7, 0xeb));
        g.fillRect(0, 0, SHOT_W, 40);
        g.setColor(new Color(0xef, 0x44, 0x44));
        g.fillOval(12, 12, 14, 14);
        g.setColor(new Color(0xf5, 0x9e, 0x0b));
        g.fillOval(34, 12, 14, 14);
        g.setColor(new Color(0x22, 0xc5, 0x5e));
        g.fillOval(56, 12, 14, 14);
        // Mock product cards
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 3; col++) {
                int x = 40 + col * 250;
                int y = 80 + row * 200;
                g.setColor(new Color(255, 255, 255));
                g.fillRoundRect(x, y, 210, 170, 8, 8);
                g.setColor(new Color(0xcb, 0xd5, 0xe1));
                g.drawRoundRect(x, y, 210, 170, 8, 8);
                g.setColor(new Color(0x6b, 0x72, 0x80));
                g.fillRect(x + 10, y + 10, 190, 90);
                g.setColor(new Color(0x0f, 0x17, 0x2a));
                g.setFont(new Font("SansSerif", Font.BOLD, 13));
                g.drawString("Product " + (row * 3 + col + 1), x + 12, y + 122);
                g.setColor(new Color(0x64, 0x74, 0x8b));
                g.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g.drawString("$" + (9 + row * 10 + col * 3) + ".99", x + 12, y + 145);
            }
        }
        // Caption watermark
        g.setColor(new Color(0x0f, 0x17, 0x2a, 180));
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.drawString(caption, 20, SHOT_H - 20);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", baos);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return new Screenshot("data:image/png;base64," + b64, Instant.now(), caption, SHOT_W, SHOT_H);
    }

    private static Duration randomDuration(int minMs, int maxMs) {
        return Duration.ofMillis(minMs + RNG.nextInt(maxMs - minMs));
    }
}
