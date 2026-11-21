package com.capstone.adproject.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.capstone.adproject.exception.GlobalExceptionHandler;
import com.capstone.adproject.service.MonitoringService;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Container-level errors (unknown URLs, failures outside controllers such as in filters, security rejections that
 * are forwarded here). Uses the same titles, messages and reference ids as {@link GlobalExceptionHandler}.
 */
@Controller
public class ApplicationErrorController implements ErrorController {

    private final MonitoringService monitoringService;

    public ApplicationErrorController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String errorId = GlobalExceptionHandler.currentErrorId();

        Object custom = request.getAttribute(GlobalExceptionHandler.CUSTOM_MESSAGE_ATTRIBUTE);
        String message = custom != null ? custom.toString() : GlobalExceptionHandler.messageFor(status);

        if (status.is5xxServerError()) {
            Object cause = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            monitoringService.incrementCriticalErrorCount();
            monitoringService.recordError(errorId, status.value(), request.getMethod(),
                    GlobalExceptionHandler.originalUri(request), cause instanceof Throwable t ? t : null);
        }
        return GlobalExceptionHandler.respond(request, status, message, errorId);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (statusCode instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
