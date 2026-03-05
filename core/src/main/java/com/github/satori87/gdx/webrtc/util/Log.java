package com.github.satori87.gdx.webrtc.util;

import java.text.SimpleDateFormat;

/**
 * Minimalist logger for gdx-webrtc.
 *
 * Can log at 4 levels: DEBUG INFO WARN ERROR, in order of severity.
 * Implemented fully static for ease of access.
 *
 * Log level can be changed by calling the appropriate log level as a static
 * function in caps e.g. Log.ERROR() sets the log level to ERROR.
 * Log level can also be changed/accessed dynamically using set/getCurrentLogLevel().
 *
 * Default log level is INFO, which suppresses DEBUG messages.
 */
public class Log {

    /** Default Log Level is INFO level. Enable DEBUG as required */
    private static LogLevel m_currentLogLevel = LogLevel.INFO;

    /** By default, just use a simple military time stamp without date. Can be changed with setTimeStampPattern() */
    private static SimpleDateFormat m_timeStampFormat = new SimpleDateFormat("HH:mm:ss");

    /**
     * Sets the Logger level to DEBUG, which means all messages are logged
     */
    public static void DEBUG() {
        setCurrentLogLevel(LogLevel.DEBUG);
    }

    /**
     * Sets the Logger level to INFO, which means all but DEBUG messages are logged
     */
    public static void INFO() {
        setCurrentLogLevel(LogLevel.INFO);
    }

    /**
     * Sets the Logger level to WARN, which means only WARN and ERROR messages are logged
     */
    public static void WARN() {
        setCurrentLogLevel(LogLevel.WARN);
    }

    /**
     * Sets the Logger level to ERROR, which means only ERROR messages are logged
     */
    public static void ERROR() {
        setCurrentLogLevel(LogLevel.ERROR);
    }

    /**
     * Sets the Logger level to NONE, which means NO messages are logged
     */
    public static void NONE() { setCurrentLogLevel(LogLevel.NONE); }

    /**
     * Logs the provided message to the DEBUG channel, if log level is set to DEBUG
     * @param message message's toString() is invoked and printed after the log level
     */
    public static void debug(Object message) {
        log(LogLevel.DEBUG, message);
    }

    /**
     * Logs the provided message to the INFO channel, if log level is set to DEBUG or INFO
     * @param message message's toString() is invoked and printed after the log level
     */
    public static void info(Object message) {
        log(LogLevel.INFO, message);
    }

    /**
     * Logs the provided exception and its stack trace to the WARN channel
     * @param e The exception that was generated somewhere in the application
     */
    public static void warn(Exception e) {
        except(LogLevel.WARN, e);
    }

    /**
     * Logs the provided message to the WARN channel
     * @param message message's toString() is invoked and printed after the log level
     */
    public static void warn(Object message) {
        log(LogLevel.WARN, message);
    }

    /**
     * Logs an exception, including full stack trace, to any log level
     * @param e The exception to be printed in full
     */
    private static void except(LogLevel atLevel, Exception e) {
        log(atLevel, e.getLocalizedMessage());
        for(StackTraceElement ste : e.getStackTrace()) {
            log(atLevel, ste.toString());
        }
    }

    /**
     * Logs the specified message to the specified log channel, if the current log level is
     * less than or equal to the specified level. Message is logged to the console.
     * @param atLevel desired channel to log in
     * @param message message's toString() is used to generate the output
     */
    private static void log(LogLevel atLevel, Object message) {
        if(atLevel.ordinal() < m_currentLogLevel.ordinal()) {
            return;
        }

        if(message == null) {
            message = "NULL";
        }

        String output = getTimeStampString() +
                " [" + atLevel + "] " +
                message;

        System.out.println(output);
    }

    /**
     * @return A string representing the current time, for use in prepending the logged messages
     */
    private static String getTimeStampString() {
        return m_timeStampFormat.format(System.currentTimeMillis());
    }

    /**
     * @return Returns the current log level as enum type LogLevel
     */
    public static LogLevel getCurrentLogLevel() {
        return m_currentLogLevel;
    }

    /**
     * @param logLevel Allows setting the current log level dynamically as
     *                 an alternative to the capitalized methods
     */
    public static void setCurrentLogLevel(LogLevel logLevel) {
        m_currentLogLevel = logLevel;
        Log.log(m_currentLogLevel, "Current Log Level set to " + m_currentLogLevel);
    }

    /**
     * @return the string pattern for the timestamp format prepending the log messages
     */
    public static String getTimeStampFormatPattern() {
        return m_timeStampFormat.toPattern();
    }

    /**
     * @param pattern the string pattern to use for prepending the time (and optionally, date) to log messages
     * @throws NullPointerException - throws a NPE if the pattern provided is null
     * @throws IllegalArgumentException - throws if pattern provided is invalid according to SDF rules
     */
    public static void setTimeStampFormatPattern(String pattern) throws NullPointerException, IllegalArgumentException {
        m_timeStampFormat = new SimpleDateFormat(pattern);
    }

    /**
     * LogLevel enum encapsulates log channel levels: DEBUG INFO WARN ERROR and NONE
     */
    public enum LogLevel {

        DEBUG(0) {
            public String toString() {
                return "DEBUG";
            }
        },
        INFO(1) {
            public String toString() {
                return "INFO";
            }
        },
        WARN(2) {
            public String toString() {
                return "WARN";
            }
        },
        ERROR(3) {
            public String toString() {
                return "ERROR";
            }
        },
        NONE(4);

        LogLevel(int level) {
        }

    }

}
