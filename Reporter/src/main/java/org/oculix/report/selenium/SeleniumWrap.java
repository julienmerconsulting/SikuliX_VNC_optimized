package org.oculix.report.selenium;

import java.lang.reflect.Method;

/**
 * Runtime dispatcher that wraps a user's {@code WebDriver} with our
 * {@link SeleniumReportingListener}. Picks the right Selenium API by
 * reflection:
 *
 * <ul>
 *   <li><b>Selenium 4+</b>: uses {@code EventFiringDecorator} with the
 *     modern {@code WebDriverListener} interface (non-deprecated).</li>
 *   <li><b>Selenium 3 / Selenium 4 pre-4.15</b>: falls back to
 *     {@code EventFiringWebDriver} + {@code WebDriverEventListener}.</li>
 * </ul>
 *
 * <p>Entirely reflective so the Reporter jar does not impose a specific
 * Selenium version on callers. If neither API is on the classpath, a
 * clear exception is thrown.
 */
public final class SeleniumWrap {

    private SeleniumWrap() {}

    public static Object wrap(Object driver) {
        if (driver == null) throw new IllegalArgumentException("driver must not be null");

        SeleniumReportingListener listener = new SeleniumReportingListener();

        // --- Try Selenium 4 (modern API) ---
        try {
            Class<?> decoratorCls = Class.forName(
                "org.openqa.selenium.support.events.EventFiringDecorator");
            Class<?> listenerIface = Class.forName(
                "org.openqa.selenium.support.events.WebDriverListener");
            Class<?> webDriverCls = Class.forName("org.openqa.selenium.WebDriver");

            // new EventFiringDecorator(new WebDriverListener[] { listener })
            Object decorator = decoratorCls
                .getDeclaredConstructor(java.lang.reflect.Array.newInstance(listenerIface, 0).getClass())
                .newInstance(new Object[] { new Object[] { listener } });

            // decorator.decorate(driver)
            Method decorate = decoratorCls.getMethod("decorate", webDriverCls);
            return decorate.invoke(decorator, driver);
        } catch (ClassNotFoundException ignored) {
            // Fall through to Selenium 3 path.
        } catch (Exception e) {
            throw new IllegalStateException(
                "Selenium 4 API present but wrap failed: " + e.getMessage(), e);
        }

        // --- Fallback: Selenium 3 or deprecated Selenium 4 ---
        try {
            Class<?> efwdCls = Class.forName(
                "org.openqa.selenium.support.events.EventFiringWebDriver");
            Class<?> webDriverCls = Class.forName("org.openqa.selenium.WebDriver");
            Class<?> eventListenerIface = Class.forName(
                "org.openqa.selenium.support.events.WebDriverEventListener");

            // new EventFiringWebDriver(driver)
            Object efwd = efwdCls.getDeclaredConstructor(webDriverCls).newInstance(driver);
            // efwd.register(listener)
            Method register = efwdCls.getMethod("register", eventListenerIface);
            register.invoke(efwd, listener);
            return efwd;
        } catch (ClassNotFoundException noSelenium) {
            throw new IllegalStateException(
                "No Selenium on classpath — add selenium-support (3.x or 4.x) to use wrapDriver.",
                noSelenium);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Selenium 3 fallback wrap failed: " + e.getMessage(), e);
        }
    }
}
