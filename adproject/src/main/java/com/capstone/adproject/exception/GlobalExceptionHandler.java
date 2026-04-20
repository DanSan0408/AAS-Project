package com.capstone.adproject.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.capstone.adproject.service.MonitoringService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MonitoringService monitoringService;

    public GlobalExceptionHandler(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNoResourceFoundException(NoResourceFoundException ex) {
        // This prevents log spam for missing resources like favicon.ico
        logger.warn("Resource not found: {}", ex.getResourcePath());
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("errorMessage", "The page you are looking for does not exist.");
        modelAndView.addObject("status", HttpStatus.NOT_FOUND.value());
        return modelAndView;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleConstraintViolationException(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Constraint violation for request URL: {}", request.getRequestURL(), ex);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("errorMessage", "There was a problem with your submission. Please check the fields and try again.");
        modelAndView.addObject("status", HttpStatus.BAD_REQUEST.value());
        return modelAndView;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ModelAndView handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.error("Data integrity violation for request URL: {}", request.getRequestURL(), ex);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("errorMessage", "A database error occurred. This could be due to a duplicate entry. Please try again.");
        modelAndView.addObject("status", HttpStatus.CONFLICT.value());
        return modelAndView;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView handleGenericException(Exception ex, HttpServletRequest request) {
        // Increment the critical error counter for any unexpected exception (5xx)
        monitoringService.incrementCriticalErrorCount();

        logger.error("Caught unhandled exception for request URL: {}", request.getRequestURL(), ex);
        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.addObject("errorMessage", "An unexpected error occurred. Please try again later or contact support.");
        modelAndView.addObject("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return modelAndView;
    }
}