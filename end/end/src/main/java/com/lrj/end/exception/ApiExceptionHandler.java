package com.lrj.end.exception;

import com.lrj.end.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    /** 未认证（token 缺失/过期/封禁）→ 401，前端统一跳登录页 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    /** 已登录但权限不足（如普通用户访问管理接口）→ 403 */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    /** 次数超限（如超出每日对话上限）→ 429 */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException exception, HttpServletRequest request) {
        return response(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<ApiError> handleAiService(AiServiceException exception, HttpServletRequest request) {
        return response(exception.getStatus(), exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof HttpMessageNotReadableException
                ? "请求内容格式不正确"
                : exception.getMessage();
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不正确");
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "数据约束校验失败，请检查会话、模型和消息数据", request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(status.value(), message, request.getRequestURI(), Instant.now());
        return ResponseEntity.status(status).body(body);
    }
}
