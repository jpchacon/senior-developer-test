package com.caseware.templateupdate.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain validation failures onto client errors.
 *
 * <p>The value types reject invalid input by throwing {@link IllegalArgumentException} from their
 * constructors — a version below 1, a blank template id. Reaching the client unmapped, those
 * surface as 500s, which is wrong twice over: it tells the caller the server is broken when the
 * request was, and it inflates the error-rate signal that operational alerting depends on.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Creates the handler. */
    public ApiExceptionHandler() {
        // Nothing to configure.
    }

    /**
     * Turns a rejected value into a 400 with the reason the domain gave.
     *
     * @param failure the validation failure raised by a domain type
     * @return a problem detail describing what the caller got wrong
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onInvalidArgument(IllegalArgumentException failure) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
        problem.setTitle("Invalid request parameter");
        return problem;
    }
}
