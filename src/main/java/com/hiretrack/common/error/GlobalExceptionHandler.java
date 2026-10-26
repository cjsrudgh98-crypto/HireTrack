package com.hiretrack.common.error;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getErrorCode().getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : ErrorCode.INVALID_INPUT.getMessage();
        return toResponse(ErrorCode.INVALID_INPUT, message);
    }

    // InvalidParameterException: 쿼리 파라미터가 UTF-8 로 디코딩되지 않을 때 Tomcat 이 던진다
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            InvalidParameterException.class})
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return toResponse(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    // 서비스에서 미리 막지 못한 동시 요청(유니크 제약 위반, 잠금 대기 초과) — 500 이 아니라 다시 시도하라는 409
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception e) {
        log.warn("request-conflict {}", e.getClass().getSimpleName());
        return toResponse(ErrorCode.CONFLICT, ErrorCode.CONFLICT.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 405·415·필수 파라미터 누락 등 Spring MVC 가 상태 코드를 정해 둔 예외는 그 코드를 그대로 쓴다
        // (같은 패키지의 ErrorResponse 와 이름이 겹쳐 Spring 쪽은 전체 이름으로 쓴다)
        if (e instanceof org.springframework.web.ErrorResponse mvc) {
            ErrorCode code = switch (mvc.getStatusCode().value()) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
                case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                default -> mvc.getStatusCode().is4xxClientError() ? ErrorCode.INVALID_INPUT : null;
            };
            if (code != null) {
                return ResponseEntity.status(code.getStatus()).headers(mvc.getHeaders())
                        .body(ErrorResponse.of(code, code.getMessage()));
            }
        }
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, message));
    }
}
