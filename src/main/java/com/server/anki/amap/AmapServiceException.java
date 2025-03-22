package com.server.anki.amap;

public class AmapServiceException extends RuntimeException {

    public AmapServiceException(String message) {
        super(message);
    }

    public AmapServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
