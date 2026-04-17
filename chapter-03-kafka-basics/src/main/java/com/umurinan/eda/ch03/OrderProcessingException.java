package com.umurinan.eda.ch03;

public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
