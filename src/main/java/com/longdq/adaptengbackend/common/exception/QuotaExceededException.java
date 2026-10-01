package com.longdq.adaptengbackend.common.exception;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends BusinessException {

    public QuotaExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}