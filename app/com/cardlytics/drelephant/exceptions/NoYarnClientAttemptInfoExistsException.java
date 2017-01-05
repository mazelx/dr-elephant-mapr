package com.cardlytics.drelephant.exceptions;

/**
 * Created by blowery on 12/29/2016.
 */
public class NoYarnClientAttemptInfoExistsException extends Exception {
    @Override
    public Throwable fillInStackTrace() {
        return null;
    }
    public NoYarnClientAttemptInfoExistsException(String message) {
            super(message);
        }
}
