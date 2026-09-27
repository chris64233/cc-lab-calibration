package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.service.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.stream.Collectors;

/** 统一异常处理：业务异常按语义映射状态码，参数校验失败返回 400。 */
@org.springframework.web.bind.annotation.RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(Instant timestamp, int status, String error, String message) {
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorBody> handleBusiness(BusinessException e) {
        HttpStatus status = e.getStatus();
        return ResponseEntity.status(status).body(
                new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(
                new ErrorBody(Instant.now(), 400, "Bad Request", message));
    }
}
