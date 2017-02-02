package com.bretlowery.drelephant.exceptions;

/**
 * Created by blowery on 12/29/2016.
 */
public class MissingHistoryServerInfoException extends Exception {
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }
    public MissingHistoryServerInfoException(String message) {
        super(message);
    }
}
