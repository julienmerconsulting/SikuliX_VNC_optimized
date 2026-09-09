# OculiX Reporter

A self-contained HTML report for OculiX runs: one file, screenshots embedded
as base64, no server, no assets folder, no network. Open it anywhere, weeks
later, and see what the run saw.

Opt-in. Existing code is untouched until you wrap a `Screen`.

```xml
<dependency>
    <groupId>io.github.oculix-org</groupId>
    <artifactId>oculixreporter</artifactId>
    <version>4.0.0</version>
</dependency>
```

**Java 17 or later**, like every OculiX module.

## Using it

```java
import org.oculix.report.OculixReporter;
import org.sikuli.script.Screen;

OculixReporter.startSuite("Nightly checkout");
OculixReporter.startTest("Add to cart");

Screen s = OculixReporter.wrapScreen();   // records every action it performs
s.click("cart_button.png");
s.wait("cart_page.png", 10);

OculixReporter.endTest();
OculixReporter.endSuite();
OculixReporter.writeTo(Paths.get("target/oculix-report.html"));
```

`wrapScreen()` returns a `ReportedScreen`, which *is* a `Screen`: pass it
anywhere a `Screen` is expected. Every find, click and type is recorded with
its screenshot, its match and its timing.

## What the report holds

- **Every step** with the screenshot it acted on, the match rectangle and the
  similarity score.
- **A diagnosis** on failure: rules read the run and say what most likely went
  wrong, rather than only that a `FindFailed` was thrown.
- **History across runs**, with flaky-test detection: a test that alternates
  between pass and fail is named as flaky instead of being blamed once.

## Test-framework integrations

| Framework | Entry point |
| --- | --- |
| JUnit 5 | `OculixJUnit5Extension` |
| TestNG | `OculixTestNGListener` |
| Selenium | `SeleniumReportingListener`, and `OculixReporter.wrapDriver(driver)` |

The Selenium wrapper means a suite that mixes a browser and the desktop lands
in a single report.

Full documentation at [oculix.org](https://oculix.org).
