package com.caseware.templateupdate.summary;

/**
 * Signals that a narrator could not produce a summary.
 *
 * <p>Thrown for the ordinary operational failures of calling a language model — timeouts, rate
 * limits, malformed responses. Callers are expected to fall back to a deterministic rendering
 * rather than propagate this to a practitioner: an unavailable model must never block a decision.
 */
public class NarrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message what went wrong
     */
    public NarrationException(String message) {
        super(message);
    }

    /**
     * Creates an exception wrapping an underlying failure.
     *
     * @param message what went wrong
     * @param cause the underlying failure
     */
    public NarrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
