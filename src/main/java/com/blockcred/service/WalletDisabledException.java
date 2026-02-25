package com.blockcred.service;

public class WalletDisabledException extends RuntimeException {
    public WalletDisabledException(String message) {
        super(message);
    }
}
