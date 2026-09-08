package com.bookflow.exception;

public class InvalidEmailDomainException extends RuntimeException {

    public InvalidEmailDomainException(String message) {
        super(message);
    }
}
