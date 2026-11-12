package com.example.swapi.adapter.in.web;

import com.example.swapi.domain.ResourceNotFoundException;
import com.example.swapi.domain.UpstreamUnavailableException;
import com.example.swapi.domain.UsernameTakenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps exceptions to RFC 9457 problem responses. Upstream details and stack traces are logged
 * with a correlation id; only the id reaches the client.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final URI NOT_FOUND_TYPE = URI.create("urn:swapi:error:not-found");
    private static final URI VALIDATION_TYPE = URI.create("urn:swapi:error:validation");
    private static final URI UPSTREAM_TYPE = URI.create("urn:swapi:error:upstream");
    private static final URI INTERNAL_TYPE = URI.create("urn:swapi:error:internal");
    static final URI UNAUTHORIZED_TYPE = URI.create("urn:swapi:error:unauthorized");
    private static final URI CONFLICT_TYPE = URI.create("urn:swapi:error:username-taken");

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "No %s exists with id '%s'".formatted(e.entity(), e.id()));
        problem.setTitle("Resource not found");
        problem.setType(NOT_FOUND_TYPE);
        return problem;
    }

    /** A 409 reveals that a username exists; accepted, since there is no email channel. */
    @ExceptionHandler(UsernameTakenException.class)
    ProblemDetail handleUsernameTaken(UsernameTakenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "That username is already taken.");
        problem.setTitle("Username taken");
        problem.setType(CONFLICT_TYPE);
        return problem;
    }

    /** Same message for an unknown user and a wrong password, to prevent user enumeration. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthenticationFailure(AuthenticationException e) {
        log.info("Failed login attempt: {}", e.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Invalid username or password.");
        problem.setTitle("Authentication failed");
        problem.setType(UNAUTHORIZED_TYPE);
        return problem;
    }

    /** 504 when SWAPI timed out (a retry may help), 502 for any other upstream failure. */
    @ExceptionHandler(UpstreamUnavailableException.class)
    ProblemDetail handleUpstream(UpstreamUnavailableException e) {
        String correlationId = newCorrelationId();
        HttpStatus status = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;

        log.error("Upstream failure [{}]: {}", correlationId, e.getMessage(), e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                e.isTimeout()
                        ? "The Star Wars API did not respond in time. Please try again."
                        : "The Star Wars API could not be reached.");
        problem.setTitle(e.isTimeout() ? "Upstream timeout" : "Upstream unavailable");
        problem.setType(UPSTREAM_TYPE);
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    /** Rule violations from the core, such as the registration rules. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(VALIDATION_TYPE);
        return problem;
    }

    /** Backstop: logged in full, answered with an opaque 500. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        String correlationId = newCorrelationId();
        log.error("Unhandled exception [{}]", correlationId, e);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
        problem.setTitle("Internal server error");
        problem.setType(INTERNAL_TYPE);
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    /**
     * Overrides the framework handler so validation errors use the same problem shape. A value
     * that cannot be converted (page=abc) gets a fixed message: the framework's names Java types.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + (error.isBindingFailure()
                        ? error.getField() + " has an invalid value"
                        : error.getDefaultMessage()))
                .distinct()
                .collect(Collectors.joining("; "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                detail.isEmpty() ? "The request parameters are invalid." : detail);
        problem.setTitle("Invalid request");
        problem.setType(VALIDATION_TYPE);
        return ResponseEntity.badRequest().body(problem);
    }

    private static String newCorrelationId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
