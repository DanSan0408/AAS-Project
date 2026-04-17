package com.capstone.adproject.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class ApplicationErrorController implements ErrorController {

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);

        ModelAndView modelAndView = new ModelAndView("error");
        modelAndView.setStatus(status);
        modelAndView.addObject("status", status.value());
        modelAndView.addObject("errorMessage", resolveMessage(status));

        return modelAndView;
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

    private String resolveMessage(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "The page you are looking for does not exist.";
        }

        if (status == HttpStatus.FORBIDDEN) {
            return "You do not have permission to access this page.";
        }

        if (status == HttpStatus.UNAUTHORIZED) {
            return "You need to sign in to continue.";
        }

        if (status == HttpStatus.BAD_REQUEST) {
            return "The request could not be processed. Please check your input and try again.";
        }

        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return "Too many requests were made. Please wait a moment and try again.";
        }

        return "An unexpected system error occurred while processing your request. Please try again later.";
    }
}
