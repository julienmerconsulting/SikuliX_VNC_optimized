/*
 * Copyright (c) 2010-2021, sikuli.org, sikulix.com - MIT license
 */
package com.sikulix.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Agnostic logger for SikuliX.
 *
 * Automatically detects the runtime environment (Katalon, SLF4J, standalone)
 * and routes logs accordingly:
 * <ul>
 *   <li>Standalone: timestamp + level + thread + message on stdout/stderr</li>
 *   <li>Katalon: delegates to com.kms.katalon.core.util.KeywordUtil</li>
 *   <li>SLF4J: delegates to SLF4J logger if present on classpath</li>
 * </ul>
 */
public class SikuliLogger {

    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final LogBackend backend;
    private static final Object slf4jLogger;

    private enum LogBackend { KATALON, SLF4J, CONSOLE }

    static {
        LogBackend detected = LogBackend.CONSOLE;
        Object logger = null;

        // Try Katalon
        try {
            Class.forName("com.kms.katalon.core.util.KeywordUtil");
            detected = LogBackend.KATALON;
        } catch (ClassNotFoundException e) {
            // Try SLF4J
            try {
                Class<?> factoryClass = Class.forName("org.slf4j.LoggerFactory");
                java.lang.reflect.Method getLogger = factoryClass.getMethod("getLogger", String.class);
                logger = getLogger.invoke(null, "SikuliX");
                detected = LogBackend.SLF4J;
            } catch (Exception ex) {
                // CONSOLE fallback
            }
        }

        backend = detected;
        slf4jLogger = logger;
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void debug(String message) {
        log("DEBUG", message);
    }

    private static void log(String level, String message) {
        switch (backend) {
            case KATALON:
                logKatalon(level, message);
                break;
            case SLF4J:
                logSlf4j(level, message);
                break;
            case CONSOLE:
            default:
                logConsole(level, message);
                break;
        }
    }

    private static void logConsole(String level, String message) {
        String timestamp = new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date());
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s] [%s] %s", timestamp, level, threadName, message);

        if ("ERROR".equals(level)) {
            System.err.println(formatted);
        } else {
            System.out.println(formatted);
        }
    }

    private static void logKatalon(String level, String message) {
        try {
            Class<?> kwUtil = Class.forName("com.kms.katalon.core.util.KeywordUtil");
            String methodName;
            switch (level) {
                case "ERROR":
                    methodName = "markFailed";
                    break;
                case "WARN":
                    methodName = "markWarning";
                    break;
                default:
                    methodName = "logInfo";
                    break;
            }
            java.lang.reflect.Method m = kwUtil.getMethod(methodName, String.class);
            m.invoke(null, "[SikuliX] " + message);
        } catch (Exception e) {
            // Fallback to console
            logConsole(level, message);
        }
    }

    private static void logSlf4j(String level, String message) {
        if (slf4jLogger == null) {
            logConsole(level, message);
            return;
        }

        try {
            String methodName = level.toLowerCase();
            if ("warn".equals(methodName) || "error".equals(methodName)
                || "info".equals(methodName) || "debug".equals(methodName)) {
                java.lang.reflect.Method m = slf4jLogger.getClass().getMethod(methodName, String.class);
                m.invoke(slf4jLogger, message);
            } else {
                java.lang.reflect.Method m = slf4jLogger.getClass().getMethod("info", String.class);
                m.invoke(slf4jLogger, message);
            }
        } catch (Exception e) {
            logConsole(level, message);
        }
    }
}
