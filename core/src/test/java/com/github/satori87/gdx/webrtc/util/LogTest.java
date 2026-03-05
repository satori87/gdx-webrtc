package com.github.satori87.gdx.webrtc.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class LogTest {

    private final PrintStream originalOut = System.out;

    @AfterEach
    void restoreLogLevel() {
        Log.setCurrentLogLevel(Log.LogLevel.INFO);
        System.setOut(originalOut);
    }

    private String captureOutput(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        action.run();
        System.setOut(originalOut);
        return baos.toString();
    }

    // --- Default state ---

    @Test
    void defaultLogLevelIsInfo() {
        assertEquals(Log.LogLevel.INFO, Log.getCurrentLogLevel());
    }

    // --- Level setters ---

    @Test
    void debugSetsLevelToDebug() {
        Log.DEBUG();
        assertEquals(Log.LogLevel.DEBUG, Log.getCurrentLogLevel());
    }

    @Test
    void infoSetsLevelToInfo() {
        Log.DEBUG();
        Log.INFO();
        assertEquals(Log.LogLevel.INFO, Log.getCurrentLogLevel());
    }

    @Test
    void warnSetsLevelToWarn() {
        Log.WARN();
        assertEquals(Log.LogLevel.WARN, Log.getCurrentLogLevel());
    }

    @Test
    void errorSetsLevelToError() {
        Log.ERROR();
        assertEquals(Log.LogLevel.ERROR, Log.getCurrentLogLevel());
    }

    @Test
    void noneSetsLevelToNone() {
        Log.NONE();
        assertEquals(Log.LogLevel.NONE, Log.getCurrentLogLevel());
    }

    @Test
    void setCurrentLogLevelSetsLevel() {
        Log.setCurrentLogLevel(Log.LogLevel.WARN);
        assertEquals(Log.LogLevel.WARN, Log.getCurrentLogLevel());
    }

    // --- Filtering ---

    @Test
    void debugMessageShownAtDebugLevel() {
        Log.DEBUG();
        String output = captureOutput(() -> Log.debug("test debug"));
        assertTrue(output.contains("[DEBUG]"));
        assertTrue(output.contains("test debug"));
    }

    @Test
    void debugMessageSuppressedAtInfoLevel() {
        Log.INFO();
        String output = captureOutput(() -> Log.debug("hidden"));
        assertFalse(output.contains("hidden"));
    }

    @Test
    void infoMessageShownAtInfoLevel() {
        Log.INFO();
        String output = captureOutput(() -> Log.info("test info"));
        assertTrue(output.contains("[INFO]"));
        assertTrue(output.contains("test info"));
    }

    @Test
    void infoMessageSuppressedAtWarnLevel() {
        Log.WARN();
        String output = captureOutput(() -> Log.info("hidden"));
        assertFalse(output.contains("hidden"));
    }

    @Test
    void warnMessageShownAtWarnLevel() {
        Log.WARN();
        String output = captureOutput(() -> Log.warn("test warn"));
        assertTrue(output.contains("[WARN]"));
        assertTrue(output.contains("test warn"));
    }

    @Test
    void warnMessageSuppressedAtErrorLevel() {
        Log.ERROR();
        String output = captureOutput(() -> Log.warn("hidden"));
        assertFalse(output.contains("hidden"));
    }

    @Test
    void noneLogsNothing() {
        Log.NONE();
        String output = captureOutput(() -> {
            Log.debug("hidden");
            Log.info("hidden");
            Log.warn("hidden");
        });
        assertFalse(output.contains("hidden"));
    }

    @Test
    void debugLevelShowsAllMessages() {
        Log.DEBUG();
        String output = captureOutput(() -> {
            Log.debug("d");
            Log.info("i");
            Log.warn("w");
        });
        assertTrue(output.contains("[DEBUG]"));
        assertTrue(output.contains("[INFO]"));
        assertTrue(output.contains("[WARN]"));
    }

    // --- Null handling ---

    @Test
    void nullMessageDoesNotThrow() {
        Log.DEBUG();
        String output = captureOutput(() -> Log.debug(null));
        assertTrue(output.contains("NULL"));
    }

    // --- Exception logging ---

    @Test
    void warnExceptionLogsMessageAndStackTrace() {
        Log.WARN();
        Exception e = new RuntimeException("test error");
        String output = captureOutput(() -> Log.warn(e));
        assertTrue(output.contains("test error"));
        assertTrue(output.contains("LogTest"));
    }

    // --- Timestamp format ---

    @Test
    void defaultTimestampPattern() {
        assertEquals("HH:mm:ss", Log.getTimeStampFormatPattern());
    }

    @Test
    void setTimestampFormatPattern() {
        Log.setTimeStampFormatPattern("yyyy-MM-dd HH:mm:ss");
        assertEquals("yyyy-MM-dd HH:mm:ss", Log.getTimeStampFormatPattern());
        // Restore default
        Log.setTimeStampFormatPattern("HH:mm:ss");
    }

    @Test
    void invalidTimestampPatternThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Log.setTimeStampFormatPattern("QQQQQ"));
    }

    @Test
    void nullTimestampPatternThrows() {
        assertThrows(NullPointerException.class,
                () -> Log.setTimeStampFormatPattern(null));
    }

    // --- LogLevel enum ---

    @Test
    void logLevelOrdinalOrder() {
        assertTrue(Log.LogLevel.DEBUG.ordinal() < Log.LogLevel.INFO.ordinal());
        assertTrue(Log.LogLevel.INFO.ordinal() < Log.LogLevel.WARN.ordinal());
        assertTrue(Log.LogLevel.WARN.ordinal() < Log.LogLevel.ERROR.ordinal());
        assertTrue(Log.LogLevel.ERROR.ordinal() < Log.LogLevel.NONE.ordinal());
    }

    @Test
    void logLevelToString() {
        assertEquals("DEBUG", Log.LogLevel.DEBUG.toString());
        assertEquals("INFO", Log.LogLevel.INFO.toString());
        assertEquals("WARN", Log.LogLevel.WARN.toString());
        assertEquals("ERROR", Log.LogLevel.ERROR.toString());
    }

    // --- Output format ---

    @Test
    void outputContainsTimestampAndLevel() {
        Log.DEBUG();
        String output = captureOutput(() -> Log.info("hello"));
        // Should contain timestamp (HH:mm:ss format), [INFO], and the message
        assertTrue(output.matches("(?s).*\\d{2}:\\d{2}:\\d{2} \\[INFO\\] hello.*"));
    }
}
