package com.forfun.code_lineage.controller;

import com.forfun.code_lineage.controller.dto.LineageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public LineageResponse handleMissingParam(MissingServletRequestParameterException e) {
        return LineageResponse.builder()
                .success(false)
                .error("Missing required parameter: " + e.getParameterName())
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public LineageResponse handleIllegalArgument(IllegalArgumentException e) {
        return LineageResponse.builder()
                .success(false)
                .error(e.getMessage())
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public LineageResponse handleGeneral(Exception e) {
        // Suppress noisy 404 logs for actuator probes and favicon
        String msg = e.getMessage();
        if (msg != null && (msg.contains("actuator") || msg.contains("favicon"))) {
            return LineageResponse.builder().success(false).error("Not found").build();
        }
        log.error("Unhandled exception: {}", e.getMessage(), e);
        return LineageResponse.builder()
                .success(false)
                .error(msg != null ? msg : "Internal server error")
                .build();
    }
}
