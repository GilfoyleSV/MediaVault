package ru.kuzmin.mediavault.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UploadRateLimitExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadRateLimit(UploadRateLimitExceededException error) {
        var status = HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(error.retryAfterSeconds()))
                .body(ProblemDetail.forStatusAndDetail(status, error.getMessage()));
    }

    @ExceptionHandler(MediaNotFoundException.class)
    ProblemDetail handleNotFound(MediaNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(InvalidMediaStateException.class)
    ProblemDetail handleInvalidState(InvalidMediaStateException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler({InvalidFileSizeException.class, UnsupportedMediaTypeException.class})
    ProblemDetail handleBadRequest(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler(StorageException.class)
    ProblemDetail handleStorageException(StorageException error) {
        log.warn("Object storage request failed", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, error.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError field : error.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(field.getField(), field.getDefaultMessage());
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        body.setProperty("violations", violations);
        return handleExceptionInternal(error, body, headers, status, request);
    }
}
