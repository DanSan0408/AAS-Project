package com.capstone.adproject.exception;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.capstone.adproject.service.MonitoringService;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

/**
 * Single place that turns exceptions into a user-facing response. Every failure gets a short reference id that is
 * shown to the user and written next to the stack trace in the log, so a screenshot of the error page is enough to
 * find the matching log entry. Every failure is also recorded in {@link MonitoringService} for the Super Admin
 * monitoring page.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MonitoringService monitoringService;

    public GlobalExceptionHandler(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    /** Request attribute carrying a safe, user-facing message from code that forwards to /error. */
    public static final String CUSTOM_MESSAGE_ATTRIBUTE = "iams.errorMessage";

    // ---- helpers shared with ApplicationErrorController and SecurityConfig ----

    /** True for fetch/XHR/API callers that expect JSON rather than an HTML page. */
    public static boolean wantsJson(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) {
            return true;
        }
        String uri = originalUri(request);
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        if (uri != null && uri.startsWith(context + "/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json") && !accept.contains("text/html");
    }

    /** The URI the user actually requested, even when we are inside a forward to /error. Never includes the query. */
    public static String originalUri(HttpServletRequest request) {
        Object forwarded = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (forwarded == null) {
            forwarded = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        }
        return forwarded != null ? forwarded.toString() : request.getRequestURI();
    }

    public static String titleFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "We couldn't process that request";
            case UNAUTHORIZED -> "Please sign in";
            case FORBIDDEN -> "Access denied";
            case NOT_FOUND -> "Page not found";
            case METHOD_NOT_ALLOWED -> "That action isn't available here";
            case CONFLICT -> "That conflicts with existing data";
            case PAYLOAD_TOO_LARGE -> "File too large";
            case TOO_MANY_REQUESTS -> "Too many requests";
            case SERVICE_UNAVAILABLE -> "Temporarily unavailable";
            default -> status.is5xxServerError() ? "Something went wrong on our side" : "Something went wrong";
        };
    }

    public static String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request could not be processed. Please check your input and try again.";
            case UNAUTHORIZED -> "You need to sign in to continue.";
            case FORBIDDEN -> "You do not have permission to open this page.";
            case NOT_FOUND -> "The page you are looking for does not exist or has moved.";
            case METHOD_NOT_ALLOWED -> "This address can't be used that way. Go back and use the buttons on the page.";
            case CONFLICT -> "This looks like a duplicate of something that already exists. Please review and try again.";
            case PAYLOAD_TOO_LARGE -> "The file you uploaded is larger than the allowed size.";
            case TOO_MANY_REQUESTS -> "Too many requests were made. Please wait a moment and try again.";
            case SERVICE_UNAVAILABLE -> "The service is busy or being updated. Please try again in a minute.";
            default -> "An unexpected error occurred. Please try again; if it keeps happening, contact support and quote the reference below.";
        };
    }

    /** Reference shown to the user. Reuses the request id already stamped on this request's log lines. */
    public static String currentErrorId() {
        String id = MDC.get("requestId");
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return id.length() > 8 ? id.substring(0, 8).toUpperCase(Locale.ROOT) : id.toUpperCase(Locale.ROOT);
    }

    /** Builds the HTML error view, or a JSON body for script callers. */
    public static Object respond(HttpServletRequest request, HttpStatus status, String message, String errorId) {
        if (wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status.value());
            body.put("error", titleFor(status));
            body.put("message", message);
            body.put("errorId", errorId);
            return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
        }
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("errorTitle", titleFor(status));
        modelAndView.addObject("errorMessage", message);
        modelAndView.addObject("errorId", errorId);
        return modelAndView;
    }

    private Object fail(HttpServletRequest request, HttpStatus status, String message, Exception ex) {
        String errorId = currentErrorId();
        monitoringService.recordError(errorId, status.value(), request.getMethod(), originalUri(request), ex);
        return respond(request, status, message, errorId);
    }

    // ---- handlers ----

    /** Security exceptions belong to Spring Security (login redirect / 403 page), not to the 500 handler below. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ModelAndView handleAsyncTimeout(AsyncRequestTimeoutException ex, HttpServletRequest request) {
        // SSE connections time out normally when clients disconnect; do not attempt to write an HTML error page
        logger.debug("Async request timeout for URL: {}", originalUri(request));
        return null;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        logger.warn("Missing request parameter for URL {}: {}", originalUri(request), ex.getMessage());
        return fail(request, HttpStatus.BAD_REQUEST,
                "A required parameter was missing or invalid: " + ex.getParameterName(), ex);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        logger.warn("Resource not found: {}", ex.getResourcePath());
        return fail(request, HttpStatus.NOT_FOUND, messageFor(HttpStatus.NOT_FOUND), ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logger.warn("Method {} not supported for {}", ex.getMethod(), originalUri(request));
        return fail(request, HttpStatus.METHOD_NOT_ALLOWED, messageFor(HttpStatus.METHOD_NOT_ALLOWED), ex);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        logger.warn("Upload too large for {}: {}", originalUri(request), ex.getMessage());
        return fail(request, HttpStatus.PAYLOAD_TOO_LARGE, messageFor(HttpStatus.PAYLOAD_TOO_LARGE), ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Object handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Constraint violation for request URL: {}", originalUri(request), ex);
        return fail(request, HttpStatus.BAD_REQUEST,
                "There was a problem with your submission. Please check the fields and try again.", ex);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        if (isSchemaDrift(ex)) {
            return handleSchemaDrift(ex, request);
        }
        logger.error("Data integrity violation for request URL: {}", originalUri(request), ex);
        return fail(request, HttpStatus.CONFLICT, messageFor(HttpStatus.CONFLICT), ex);
    }

    /**
     * The database no longer matches what the code expects (a column was renamed or dropped, a legacy column became
     * mandatory...). Report it as a distinct, alertable incident instead of a generic 500.
     */
    @ExceptionHandler(DataAccessException.class)
    public Object handleDataAccess(DataAccessException ex, HttpServletRequest request, HttpServletResponse response) {
        if (isSchemaDrift(ex)) {
            return handleSchemaDrift(ex, request);
        }
        return handleGenericException(ex, request, response);
    }

    private Object handleSchemaDrift(DataAccessException ex, HttpServletRequest request) {
        monitoringService.recordSchemaIncident(summarizeDataFault(ex));
        logger.error("SCHEMA DRIFT detected for request URL: {}: {}", originalUri(request), summarizeDataFault(ex), ex);
        return fail(request, HttpStatus.SERVICE_UNAVAILABLE,
                "Part of the system is being updated and this action can't be completed right now. "
                        + "The team has been notified. Please try again later.", ex);
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (ex instanceof AsyncRequestTimeoutException) {
            return null;
        }
        if (response != null && response.isCommitted()) {
            logger.warn("Response already committed for URL: {}. Skipping error view: {}", originalUri(request), ex.getMessage());
            return null;
        }

        monitoringService.incrementCriticalErrorCount();
        logger.error("Caught unhandled exception for request URL: {}", originalUri(request), ex);
        return fail(request, HttpStatus.INTERNAL_SERVER_ERROR, messageFor(HttpStatus.INTERNAL_SERVER_ERROR), ex);
    }

    /** Database errors that mean the schema and the code disagree, rather than bad user input. */
    public static boolean isSchemaDrift(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("unknown column")
                    || lower.contains("doesn't have a default value")
                    || lower.contains("data truncated for column")
                    || (lower.contains("table") && lower.contains("doesn't exist"))) {
                return true;
            }
        }
        return false;
    }

    /** The innermost database message, trimmed, for the monitoring page and alerts. */
    public static String summarizeDataFault(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 300 ? message.substring(0, 300) + "..." : message;
    }
}
