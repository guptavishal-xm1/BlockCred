package com.blockcred.service;

public class ChainUnavailableException extends RuntimeException {
    public ChainUnavailableException(String message) {
        super(message);
    }
}
