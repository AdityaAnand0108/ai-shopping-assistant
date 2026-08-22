package com.shopassist.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 problem documents.
 *
 * <p>The point of routing everything through here is that no response body is
 * ever assembled from an exception message the client did not already know
 * about. A stack trace, a SQL fragment or a constraint name reaching the browser
 * would hand an attacker a map of the schema — precisely what the brief's
 * guardrail requirement rules out.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://shop-assistant.local/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields were rejected.", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail handleAuthenticationFailed(AuthenticationFailedException ex) {
        // Same response whether the username was unknown, the password was wrong,
        // or the account is disabled.
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid username or password.", "authentication");
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException ex) {
        ProblemDetail problem = problem(HttpStatus.LOCKED, "Account temporarily locked",
                "Too many failed sign-in attempts. Try again later.", "account-locked");
        problem.setProperty("lockedUntil", ex.getLockedUntil());
        return problem;
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ProblemDetail handleDuplicateAccount(DuplicateAccountException ex) {
        return problem(HttpStatus.CONFLICT, "Account already exists",
                ex.getMessage(), "duplicate-account");
    }

    /**
     * Last resort. Logs the real cause server-side and returns nothing useful to
     * the caller — an unexpected failure must not become a disclosure channel.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong",
                "The request could not be completed. Please try again.", "internal");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
