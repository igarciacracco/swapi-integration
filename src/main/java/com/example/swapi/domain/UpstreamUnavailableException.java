package com.example.swapi.domain;

/** SWAPI failed or answered with something unusable. {@code timeout} selects 504 over 502. */
public class UpstreamUnavailableException extends RuntimeException {

    private final boolean timeout;

    public UpstreamUnavailableException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public static UpstreamUnavailableException failed(String message, Throwable cause) {
        return new UpstreamUnavailableException(message, cause, false);
    }

    public static UpstreamUnavailableException timedOut(String message, Throwable cause) {
        return new UpstreamUnavailableException(message, cause, true);
    }

    public boolean isTimeout() {
        return timeout;
    }
}
