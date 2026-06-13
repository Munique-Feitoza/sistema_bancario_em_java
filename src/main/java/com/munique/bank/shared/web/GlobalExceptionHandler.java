// src/main/java/com/munique/bank/shared/web/GlobalExceptionHandler.java
package com.munique.bank.shared.web;

import com.munique.bank.shared.exception.AccessForbiddenException;
import com.munique.bank.shared.exception.AuthenticationFailedException;
import com.munique.bank.iam.domain.exception.TooManyLoginAttemptsException;
import com.munique.bank.shared.exception.BusinessRuleException;
import com.munique.bank.shared.exception.DomainException;
import com.munique.bank.shared.exception.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traducao centralizada de excecoes para respostas RFC 7807
 * (application/problem+json). Nenhuma stack trace ou detalhe interno vaza para
 * o cliente; erros inesperados sao logados no servidor e respondem 500 generico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Excecoes de dominio ----------

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.CONFLICT, "Business Rule Violation", ex.getMessage());
    }

    @ExceptionHandler(AccessForbiddenException.class)
    public ProblemDetail handleForbidden(AccessForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ProblemDetail handleAuthFailure(AuthenticationFailedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ProblemDetail handleTooManyAttempts(TooManyLoginAttemptsException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable Entity", ex.getMessage());
    }

    // ---------- Concorrencia / integridade (caminho critico financeiro) ----------

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ProblemDetail handleLockFailure(Exception ex) {
        // Conflito de concorrencia detectado pelo lock otimista/pessimista — operacao deve ser repetida.
        return problem(HttpStatus.CONFLICT, "Concurrent Modification",
                "The resource was modified concurrently. Please retry the operation.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Data Integrity Violation",
                "The request conflicts with the current state of the resource.");
    }

    // ---------- Erros de protocolo / entrada ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage() : "invalid value"))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request",
                "Parameter '" + ex.getName() + "' has an invalid value");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request",
                "Request body is missing or malformed");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
                "The request media type is not supported");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", "The requested resource was not found");
    }

    // ---------- Seguranca / fallback ----------

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleSpringAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden",
                "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error handling request", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
