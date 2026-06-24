package com.longdq.adaptengbackend.exception;

import org.springframework.http.HttpStatus;

public class AIProcessingException extends BusinessException {

    public AIProcessingException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public AIProcessingException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
