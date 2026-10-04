package dev.yetpk.retrace.web;

import dev.yetpk.retrace.service.error.CredentialTakenException;
import dev.yetpk.retrace.service.error.NotFoundException;
import dev.yetpk.retrace.service.error.QuotaExceededException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Turns domain and framework failures into RFC 7807 {@code ProblemDetail}s. The services are
 * deliberately HTTP-agnostic, so this is the one place the mapping from "what went wrong" to "what
 * the caller sees" lives.
 *
 * <p><b>Nothing here leaks a credential</b> — not in a message, not in a property, not in a log line.
 * A key appears by id or not at all.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Missing and "belongs to someone else" deliberately produce the identical response, so an id
     * cannot be probed for existence across accounts.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e) {
        return toProblem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    /**
     * Carries both byte figures as properties, so a caller can say what to do about it rather than
     * only that it failed.
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ProblemDetail handleQuotaExceeded(QuotaExceededException e) {
        ProblemDetail problem = toProblem(HttpStatus.CONTENT_TOO_LARGE, "Storage quota exceeded", e.getMessage());
        problem.setProperty("requestedBytes", e.getRequestedBytes());
        problem.setProperty("remainingBytes", e.getRemainingBytes());
        return problem;
    }

    @ExceptionHandler(CredentialTakenException.class)
    public ProblemDetail handleCredentialTaken(CredentialTakenException e) {
        return toProblem(HttpStatus.CONFLICT, "Already registered", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ProblemDetail problem = toProblem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The request has %d invalid field(s)".formatted(errors.size()));
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * The service-layer constructors guard their contracts with {@code IllegalArgumentException}, and
     * the controllers raise the same for a broken multipart pairing. Both are the caller's mistake,
     * so neither may surface as a 500.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return toProblem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException e) {
        return toProblem(HttpStatus.BAD_REQUEST, "Invalid request",
                "The '%s' part is required".formatted(e.getRequestPartName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return toProblem(HttpStatus.CONTENT_TOO_LARGE, "Upload too large",
                "The upload exceeds the configured maximum of %d bytes".formatted(e.getMaxUploadSize()));
    }

    /**
     * The quota race the {@code @Version} column on {@code Project} exists to catch. It is a
     * legitimate concurrent write, not a server fault, and must read as something to retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentWrite(OptimisticLockingFailureException e) {
        log.info("Refused a write that lost an optimistic lock; the caller should retry");
        return toProblem(HttpStatus.CONFLICT, "Recorded concurrently",
                "Another write to this project landed first. Retry the request.");
    }

    /** No detail about which part of the credential was wrong, so nothing can be probed. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException e) {
        return toProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Valid credentials are required");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return toProblem(HttpStatus.FORBIDDEN, "Forbidden",
                "This credential may not perform this action");
    }

    private static ProblemDetail toProblem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
