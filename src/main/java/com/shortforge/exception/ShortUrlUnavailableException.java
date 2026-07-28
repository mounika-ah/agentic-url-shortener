package com.shortforge.exception;

public class ShortUrlUnavailableException extends RuntimeException {

    public ShortUrlUnavailableException(String message) {
        super(message);
    }
}