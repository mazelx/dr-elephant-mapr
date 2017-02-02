package com.bretlowery.drelephant.exceptions;

/**
 * Created by blowery on 12/29/2016.
 */
public class InvalidJSONResponseException extends Exception {
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }
    public InvalidJSONResponseException(String message) {
            super(message);
        }
}
