package com.cardlytics.drelephant.exceptions;

/**
 * Created by blowery on 12/29/2016.
 */
public class MissingYarnHistoryServerInfoException extends Exception {
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }
    public MissingYarnHistoryServerInfoException(String message) {
        super(message);
    }
}
