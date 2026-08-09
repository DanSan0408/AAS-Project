package com.capstone.adproject.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CspController {

    private static final Logger logger = LoggerFactory.getLogger(CspController.class);

    /**
     * Endpoint to receive and log Content Security Policy (CSP) violation reports from browsers.
     * This endpoint is intentionally unauthenticated to allow browsers to send reports freely.
     *
     * @param report The JSON report sent by the browser, as a raw string.
     */
    @PostMapping(value = "/csp-violations", consumes = "application/csp-report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void handleCspReport(@RequestBody String report) {
        logger.warn("CSP Violation Report Received: {}", report);
    }
}