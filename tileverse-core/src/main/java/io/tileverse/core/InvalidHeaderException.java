package io.tileverse.core;

/**
 * Exception thrown when a PMTiles header is invalid.
 */
@SuppressWarnings("serial")
public class InvalidHeaderException extends Exception {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidHeaderException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InvalidHeaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
