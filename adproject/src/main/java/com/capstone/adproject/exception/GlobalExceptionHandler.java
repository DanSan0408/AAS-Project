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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Fallback for unhandled validation errors
    @ExceptionHandler(ConstraintViolationException.class)
    public ModelAndView handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Constraint violation at {}: {}", request.getRequestURI(), ex.getMessage());
        
        ModelAndView mav = new ModelAndView("error"); // Assumes a generic error.html template exists
        mav.addObject("errorMessage", "Validation failed for the submitted data. Please verify your inputs and try again.");
        return mav;
    }

    // Fallback for database integrity violations (e.g., duplicate unique keys not explicitly caught)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        logger.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
        
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("errorMessage", "A database conflict occurred. This is often caused by submitting duplicate information.");
        return mav;
    }

    // Gracefully handle 404 Missing Files (like favicon.ico) without spamming the terminal
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView handleNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        logger.warn("Resource not found: {}", request.getRequestURI());
        
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("errorMessage", "The requested resource could not be found.");
        return mav;
    }

    // Catch-all for generic exceptions to prevent stack trace leaks
    @ExceptionHandler(Exception.class)
    public ModelAndView handleGenericException(Exception ex, HttpServletRequest request) {
        // Log the full stack trace server-side ONLY for debugging
        logger.error("Unexpected system error occurred at {}", request.getRequestURI(), ex);

        ModelAndView mav = new ModelAndView("error");
        mav.addObject("errorMessage", "An unexpected system error occurred. Please try again later or contact the administrator.");
        return mav;
    }
}
