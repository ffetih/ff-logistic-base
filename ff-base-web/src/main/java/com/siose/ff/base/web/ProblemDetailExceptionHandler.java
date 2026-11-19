package com.siose.ff.base.web;

import com.siose.ff.base.tenant.TenantAccessException;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into RFC 9457 problem responses, the same way in every product.
 * Authentication (401) and role (403) failures are answered by Spring Security, not here.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler {

    /** Problem types are stable identifiers (RFC 9457 section 3.1.1), not links. */
    static final String PROBLEM_TYPE_PREFIX = "urn:siose-ff:problem:";

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage(),
                "Domain Validation Error", "domain-validation");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage(),
                "Resource Not Found", "not-found");
    }

    @ExceptionHandler(TenantAccessException.class)
    public ProblemDetail handleTenantAccess(TenantAccessException ex) {
        return problem(HttpStatus.FORBIDDEN, ex.getMessage(),
                "Multi-Tenant Access Denied", "tenant-access-denied");
    }

    /**
     * Two requests changed the same record at once (an optimistic {@code @Version} check or a lock
     * timeout). The losing request changed nothing; repeating it is safe.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ConcurrencyFailureException ex) {
        return problem(HttpStatus.CONFLICT,
                "The record was changed by another request at the same time. Load it again and retry.",
                "Concurrent Update", "concurrent-update");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage(), ex.problemTitle(), ex.problemSlug());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ProblemDetail handleDependencyUnavailable(DependencyUnavailableException ex) {
        // The caller only sees the message; the cause (refused, timed out, rejected) is for the operator.
        // The innermost cause says the most: client libraries wrap it in generic "error while ..." text.
        Throwable cause = ex.getCause() == null ? null : NestedExceptionUtils.getMostSpecificCause(ex.getCause());
        log.warn("{} ({})", ex.getMessage(), cause == null ? "no cause" : cause.toString());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex.problemTitle(), ex.problemSlug());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, errors,
                "Request Payload Validation Failed", "invalid-payload");
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String title, String typeSlug) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + typeSlug));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
