package com.shopassist.advice;

import com.shopassist.exception.InvalidRequestException;
import com.shopassist.exception.ResourceNotFoundException;
import com.shopassist.exception.ai.GuardrailViolationException;
import com.shopassist.exception.ai.ModelUnavailableException;
import com.shopassist.exception.ai.RateLimitExceededException;
import com.shopassist.exception.auth.AccountLockedException;
import com.shopassist.exception.auth.AuthenticationFailedException;
import com.shopassist.exception.auth.DuplicateAccountException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * Covers both request bodies and query parameters: the body case throws
     * MethodArgumentNotValidException, which extends BindException.
     */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleValidation(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields were rejected.", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        // Identical whether the resource is missing or simply not the caller's.
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage(), "invalid-request");
    }

    /**
     * A query parameter that cannot be converted — a non-numeric price, or a
     * sort key outside the allowlist. The parameter name is safe to echo since
     * the client sent it; the offending value and the target type are not, as
     * the type would name an internal class.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The value supplied for '" + ex.getName() + "' is not valid.", "invalid-request");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Required parameter '" + ex.getParameterName() + "' is missing.", "invalid-request");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        // Jackson's message quotes the offending JSON and names the target class;
        // neither belongs in a response.
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The request body could not be read as JSON.", "invalid-request");
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
     * A message was refused before it reached the model.
     *
     * <p>200 rather than 4xx, and carrying a normal assistant reply. A refusal
     * is part of the conversation, not a failed request, and a chat client that
     * had to special-case an error status to render one turn would be the worse
     * design. The refusal is visible in {@code insight} instead.
     */
    @ExceptionHandler(GuardrailViolationException.class)
    public ProblemDetail handleGuardrailViolation(GuardrailViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Message declined",
                ex.getMessage(), "guardrail");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimited(RateLimitExceededException ex) {
        ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, "Too many messages",
                "You are sending messages faster than the assistant can answer. "
                        + "Please wait a moment and try again.", "rate-limit");
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return problem;
    }

    /**
     * The model server is down, or returned nothing usable.
     *
     * <p>503 rather than 500 because this is a dependency being unavailable, not
     * the shop being broken, and the distinction tells a client whether retrying
     * is worth anything.
     */
    @ExceptionHandler(ModelUnavailableException.class)
    public ProblemDetail handleModelUnavailable(ModelUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Assistant unavailable",
                "The assistant is temporarily unavailable. Please try again shortly.",
                "model-unavailable");
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
