package com.capstone.adproject.config;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * Hands every ERROR log event to the alerting service, so no failure depends on someone remembering to alert about it:
 * if the code logs an error (handled request failures, scheduled jobs, mail, AI calls, database), it reaches Telegram.
 * Logback creates this appender, so it reaches the Spring bean through a static sink.
 */
public class ErrorAlertAppender extends AppenderBase<ILoggingEvent> {

    /** What an alert needs to know about one error. */
    public record ErrorReport(Instant at, String logger, String message, String exceptionClass, String exceptionMessage,
                              String rootCause, String where, String requestId, String user) {}

    private static volatile Consumer<ErrorReport> sink;
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> false);

    /** The alerting service registers itself here; null switches the relay off. */
    public static void setSink(Consumer<ErrorReport> newSink) {
        sink = newSink;
    }

    @Override
    protected void append(ILoggingEvent event) {
        Consumer<ErrorReport> target = sink;
        if (target == null || !event.getLevel().isGreaterOrEqual(Level.ERROR) || INSIDE.get()) {
            return;
        }
        String loggerName = event.getLoggerName();
        // Never alert about the alerting itself: that would loop when Telegram is the thing that is down.
        if (loggerName.startsWith("com.capstone.adproject.service.AlertingService") || loggerName.startsWith(ErrorAlertAppender.class.getName())) {
            return;
        }
        INSIDE.set(true);
        try {
            ErrorReport report = toReport(event);
            if (report != null) {
                target.accept(report);
            }
        } catch (RuntimeException ignored) {
            // An alert must never break the code that logged the error.
        } finally {
            INSIDE.set(false);
        }
    }

    static ErrorReport toReport(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        IThrowableProxy root = throwable;
        while (root != null && root.getCause() != null) {
            root = root.getCause();
        }
        if (root != null && isClientHangup(root)) {
            return null;   // the visitor closed the page: not a fault of ours
        }
        String where = null;
        if (throwable != null && throwable.getStackTraceElementProxyArray() != null) {
            for (StackTraceElementProxy frame : throwable.getStackTraceElementProxyArray()) {
                String line = frame.getStackTraceElement().toString();
                if (line.startsWith("com.capstone.adproject.")) {
                    where = line;
                    break;
                }
            }
        }
        Map<String, String> mdc = event.getMDCPropertyMap();
        String requestId = mdc == null ? null : mdc.get("requestId");
        String user = mdc == null ? null : mdc.get("username");
        return new ErrorReport(Instant.ofEpochMilli(event.getTimeStamp()), event.getLoggerName(), event.getFormattedMessage(),
                throwable == null ? null : throwable.getClassName(), throwable == null ? null : throwable.getMessage(),
                root == null || root == throwable ? null : root.getClassName() + ": " + root.getMessage(), where, requestId, user);
    }

    private static boolean isClientHangup(IThrowableProxy t) {
        String name = t.getClassName();
        String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
        return name.endsWith("ClientAbortException") || name.endsWith("AsyncRequestNotUsableException") || name.endsWith("EofException")
                || message.contains("broken pipe") || message.contains("connection reset by peer");
    }
}
