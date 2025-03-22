package com.server.anki.marketing.region;

public class RegionServiceException extends RuntimeException {

    public RegionServiceException(String message) {
        super(message);
    }

    public RegionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}