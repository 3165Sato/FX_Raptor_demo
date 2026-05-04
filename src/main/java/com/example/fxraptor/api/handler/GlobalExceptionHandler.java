package com.example.fxraptor.api.handler;

import java.time.Instant;
import java.util.stream.Collectors;

import com.example.fxraptor.api.dto.ErrorResponseDto;
import com.example.fxraptor.infra.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                         HttpServletRequest request) {
        return buildAndLog(
                request,
                HttpStatus.BAD_REQUEST,
                "JSON parse error",
                ex,
                "API JSON parse error occurred"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                         HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));

        return buildAndLog(
                request,
                HttpStatus.BAD_REQUEST,
                message.isBlank() ? "Validation error" : message,
                ex,
                "API validation error occurred"
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex,
                                                                  HttpServletRequest request) {
        return buildAndLog(
                request,
                HttpStatus.BAD_REQUEST,
                ex.getMessage() == null ? "Business validation error" : ex.getMessage(),
                ex,
                "API business validation error occurred"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                         HttpServletRequest request) {
        return buildAndLog(
                request,
                HttpStatus.CONFLICT,
                "Database constraint error",
                ex,
                "API database constraint error occurred"
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDto> handleRuntimeException(RuntimeException ex,
                                                                   HttpServletRequest request) {
        return buildAndLog(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage() == null ? "Unexpected runtime error" : ex.getMessage(),
                ex,
                "API runtime error occurred"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception ex,
                                                            HttpServletRequest request) {
        return buildAndLog(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                ex,
                "API unexpected error occurred"
        );
    }

    private ResponseEntity<ErrorResponseDto> buildAndLog(HttpServletRequest request,
                                                         HttpStatus status,
                                                         String message,
                                                         Exception ex,
                                                         String logMessage) {
        String traceId = resolveTraceId(request);
        String controllerName = resolveControllerName(request);
        String method = request.getMethod();
        String path = request.getRequestURI();

        log.error(
                "{}. traceId={}, method={}, uri={}, controller={}, exception={}, message={}",
                logMessage,
                traceId,
                method,
                path,
                controllerName,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                ex
        );

        return ResponseEntity.status(status)
                .body(new ErrorResponseDto(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        path,
                        traceId
                ));
    }

    private String resolveTraceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId == null ? "N/A" : traceId.toString();
    }

    private String resolveControllerName(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName();
        }
        return "UnknownController";
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
